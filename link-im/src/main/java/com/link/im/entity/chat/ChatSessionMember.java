package com.link.im.entity.chat;

import com.link.im.entity.base.BaseEntity;
import lombok.Data;
import lombok.experimental.Accessors;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * 用户级会话状态（per-user 收件箱视图，每人每会话一条）。
 *
 * <p>这是「聊天栏 / 最近聊天列表」里的一项，承载<b>某个用户对某个会话的私有状态</b>：
 * 读到哪、是否置顶 / 免打扰 / 隐藏。同一个 {@code chatId} 对应多条本记录——
 * 单聊 2 条（双方各一），群聊 N 条（每个成员一条），靠 {@code ownerId} 区分属于谁。
 *
 * <p>和 {@link ChatSession} 的分工：{@link ChatSession} 存「会话说到哪了」（全员共享），
 * 本表存「我读到哪了」（每人私有）。<b>未读数不在本表存储</b>，实时算出：
 * {@code unread = ChatSession.lastMsgSeq - ChatSessionMember.lastReadSeq}。这样收消息时不必
 * 给每个成员写未读，群聊也没有按成员数放大的写入。
 *
 * <p>写入时机：
 * <ul>
 *   <li>创建：用户发出 / 收到该会话第一条消息时由消息流懒创建（upsert）。</li>
 *   <li>{@code lastReadSeq}：<b>不随每条消息更新</b>，仅在用户进 / 出会话时由前端惰性上报、
 *       后端落库（可先入 Redis 再异步合并），与消息量解耦。</li>
 *   <li>{@code showTop/silence/hidden}：仅用户手动操作时写，与消息无关。</li>
 * </ul>
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月23日
 */
@Data
@Accessors(chain = true)
@Document(collection = ChatSessionMember.COLLECTION_NAME)
@CompoundIndexes({
        @CompoundIndex(name = "idx_owner_chat", def = "{'owner_id':1,'chat_id':1}", unique = true),
        @CompoundIndex(name = "idx_owner_target", def = "{'owner_id':1,'target_id':1}", unique = true),
        @CompoundIndex(name = "idx_target_owner", def = "{'target_id':1,'owner_id':1}", unique = true),

        @CompoundIndex(name = "idx_owner_seq", def = "{'owner_id':1,'last_read_seq':1}"),
        @CompoundIndex(name = "idx_owner_top", def = "{'owner_id':1,'show_top':1}")
})
public class ChatSessionMember extends BaseEntity {
    public static final String COLLECTION_NAME = "chat_session_member";

    /** 单聊 */
    public static final int TYPE_SINGLE = 1;
    /** 群聊 */
    public static final int TYPE_GROUP = 2;

    /** 这条记录属于谁的收件箱（该用户的 uid）。配合 chatId 唯一确定一条记录，建唯一复合索引。 */
    @Field("owner_id")
    @Indexed
    private ObjectId ownerId;

    /** 会话标识：单聊 single_{minUid}_{maxUid}，群聊 group_{groupId}。和 ChatSession.chatId 对应。 */
    @Field("chat_id")
    private String chatId;

    /** 会话类型：1=单聊 2=群聊 */
    private int type;

    /** 对端 id：单聊存对方 uid，群聊存 groupId。用于点开会话后定位聊天对象、渲染头像昵称。 */
    @Field("target_id")
    private ObjectId targetId;

    /** owner 已读到的消息 seq（owner 私有）。进 / 出会话时上报。配合 ChatSession.lastMsgSeq 算未读。 */
    @Field("last_read_seq")
    private int lastReadSeq;

    /** 是否置顶（owner 私有）。 */
    @Field("show_top")
    private boolean showTop;

    /** 是否免打扰（owner 私有）。 */
    private boolean silence;

    /** 是否从聊天栏移除（owner 私有）。仅隐藏列表项，不删历史消息；再次收到消息会重新出现。 */
    private boolean hidden;

    // 是否有收发能力
    private boolean active = true;

    /**
     * 创建单聊收件箱条目。chatId 由调用方按排序规则算好后传入，保证双方一致。
     */
    public ChatSessionMember createSingle(String ownerId, String targetId, String chatId) {
        return this.setOwnerId(new ObjectId(ownerId))
                .setTargetId(new ObjectId(targetId))
                .setChatId(chatId)
                .setType(TYPE_SINGLE);
    }

    /**
     * 创建群聊收件箱条目。targetId 即 groupId，chatId 形如 group_{groupId}。
     */
    public ChatSessionMember createGroup(String ownerId, String groupId, String chatId) {

        return this.setOwnerId(new ObjectId(ownerId))
                .setTargetId(new ObjectId(groupId))
                .setChatId(chatId)
                .setType(TYPE_GROUP);
    }
}
