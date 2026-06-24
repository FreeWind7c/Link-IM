package com.link.restapi.chat.model.vo;

import com.link.im.entity.chat.ChatMember;
import com.link.im.entity.chat.ChatSession;
import com.link.im.entity.user.UserInfo;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月22日
 */
@Data
@Accessors(chain = true)
public class LinkChatSessionVo {
    private String chatId;

    private int type;

    private String title;

    private String avatar;

    /** 这条会话属于谁的收件箱（该用户的 uid）。配合 conversationId 唯一确定一条记录。 */
    private String ownerId;

    /** 对端 id：单聊存对方 uid，群聊存 groupId。用于点开会话后定位聊天对象、渲染头像昵称。 */
    private String targetId;

    /** 未读数（owner 私有）。自己发的不加，收到对方消息时 +1，进入会话已读时清零。 */
    private int unreadCount;

    /** owner 已读到的消息 seq（owner 私有）。配合 lastMsgSeq 算未读、做已读回执。 */
    private int lastReadSeq;

    /** 会话内最新一条消息的 seq。用于和 lastReadSeq 比较判断是否有新消息。 */
    private int lastMsgSeq;

    /** 最后一条消息的摘要，列表直接展示（如“[图片]”“在吗”）。避免列表渲染时再查消息表。 */
    private String lastMsgSummary;

    /** 最后一条消息的时间戳。聊天栏排序用（置顶优先，其次按它倒序）。 */
    private long lastMsgTime;

    /** 是否置顶（owner 私有）。 */
    private boolean showTop;

    /** 是否免打扰（owner 私有）。 */
    private boolean silence;

    /** 是否从聊天栏移除（owner 私有）。仅隐藏列表项，不删历史消息；再次收到消息会重新出现。 */
    private boolean hidden;


    public LinkChatSessionVo createVo(ChatSession session, ChatMember member, UserInfo user) {
        return this.setChatId(session.getChatId())
                .setType(session.getType())
                .setTitle(user.getNickname())
                .setAvatar(user.getAvatar())
                .setOwnerId(member.getOwnerId().toHexString())
                .setTargetId(member.getTargetId().toHexString())
                .setLastMsgSummary(session.getLastMsgSummary())
                .setUnreadCount(session.getLastMsgSeq() - member.getLastReadSeq())
                .setLastReadSeq(member.getLastReadSeq())
                .setLastMsgSeq(session.getLastMsgSeq())
                .setLastMsgTime(session.getLastMsgTime())
                .setShowTop(member.isShowTop())
                .setSilence(member.isSilence())
                .setHidden(member.isHidden());
    }
}
