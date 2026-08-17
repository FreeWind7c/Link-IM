package com.link.im.entity.chat;

import com.link.im.entity.base.BaseEntity;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * 会话级信息（全员共享，每个会话一条）。
 *
 * <p>这是某个会话「客观存在、所有人看到都一样」的部分：最新消息 seq、最后一条摘要、
 * 最后一条时间。它不区分是谁的视角，因此<b>单聊也只有一条</b>（双方共享），
 * 群聊同样只有一条（全员共享）。
 *
 * <p>和 {@link ChatSessionMember} 的分工：本表存「会话说到哪了」，{@link ChatSessionMember} 存
 * 「某个人读到哪了」。未读数不落库，由二者实时相减得出：
 * {@code unread = ChatSession.lastMsgSeq - ChatSessionMember.lastReadSeq}。
 *
 * <p>写入时机：<b>每收到一条消息</b>由消息流更新 {@code lastMsgSeq/summary/time}。
 * 因为无论单聊群聊都只有一条记录，所以群聊 N 人发一条消息，这里也只写 1 次，
 * 不存在按成员数放大的写入。
 *
 * <p>{@code lastMsgSeq} 的权威来源是 Redis 计数器（{@code INCR chat:{chatId}:seq}），
 * 本字段是它的持久化镜像，用于服务重启 / 冷启动时恢复计数器与渲染列表。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月23日
 */
@Data
@Accessors(chain = true)
@Document(collection = ChatSession.COLLECTION_NAME)
@CompoundIndexes({
        @CompoundIndex(name = "idx_chat_seq", def = "{'chat_id':1,'last_msg_seq':1}"),
})
public class ChatSession extends BaseEntity {
    public static final String COLLECTION_NAME = "chat_session";

    /** 单聊 */
    public static final int TYPE_SINGLE = 1;
    /** 群聊 */
    public static final int TYPE_GROUP = 2;

    /** 会话标识：单聊 single_{minUid}_{maxUid}，群聊 group_{groupId}。全会话唯一，建唯一索引。 */
    @Field("chat_id")
    @Indexed
    private String chatId;

    /** 会话类型：1=单聊 2=群聊 */
    @Indexed
    private int type;

    // 0用户 1系统 2官方 3机器人
    private int category;

    @Field("last_msg_type")
    private int lastMsgType;

    /** 会话内最新一条消息的 seq。每条消息后端更新。配合各成员 lastReadSeq 实时算未读。 */
    @Field("last_msg_seq")
    private int lastMsgSeq;

    /** 最后一条消息的摘要，列表直接展示（如“[图片]”“在吗”）。避免列表渲染时再查消息表。 */
    @Field("last_msg_summary")
    private String lastMsgSummary;

    /** 最后一条消息的时间戳。聊天栏排序用（结合各成员置顶状态）。 */
    @Field("last_msg_time")
    private long lastMsgTime;

    /**
     * 创建单聊会话。chatId 由调用方按排序规则算好后传入，保证双方一致。
     */
    public ChatSession createSingle(String chatId) {
        return this.setChatId(chatId)
                .setType(TYPE_SINGLE)
                .setCategory(0);
    }

    public ChatSession createSingle(String chatId,int category) {
        return this.setChatId(chatId)
                .setType(TYPE_SINGLE)
                .setCategory(category);
    }

    /**
     * 创建群聊会话。chatId 形如 group_{groupId}。
     */
    public ChatSession createGroup(String chatId) {
        return this.setChatId(chatId)
                .setType(TYPE_GROUP);
    }
}
