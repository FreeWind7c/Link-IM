package com.link.restapi.module.chat.model.vo;

import com.link.im.entity.chat.ChatSessionMember;
import com.link.im.entity.chat.ChatSession;
import com.link.im.entity.group.GroupInfo;
import com.link.im.entity.user.UserInfo;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

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

    private String ownerId;

    private int category;

    private String targetId;

    private int unreadCount;

    private int lastReadSeq;

    private int lastMsgSeq;

    private int lastMsgType;

    private String lastMsgSummary;

    private long lastMsgTime;

    private boolean showTop;

    private boolean silence;

    private boolean hidden;

    private boolean active;

    /** 未读 @我 消息的 seq 列表（最多10，可能为空）。非空→列表显示「[有人@我]」前缀、进会话渲染跳转浮动按钮。 */
    private List<Integer> atList;


    /** 单聊：title/avatar 取对端用户的昵称、头像。 */
    public static LinkChatSessionVo createSingleVo(ChatSession session, ChatSessionMember member, UserInfo user) {
        LinkChatSessionVo vo = new LinkChatSessionVo();
        return vo.setChatId(session.getChatId())
                .setType(session.getType())
                .setTitle(user.getNickname())
                .setCategory(session.getCategory())
                .setAvatar(user.getAvatar())
                .setActive(member.isActive())
                .setLastMsgType(session.getLastMsgType())
                .setOwnerId(member.getOwnerId().toHexString())
                .setTargetId(member.getTargetId().toHexString())
                .setLastMsgSummary(session.getLastMsgSummary())
                .setUnreadCount(session.getLastMsgSeq() - member.getLastReadSeq())
                .setLastReadSeq(member.getLastReadSeq())
                .setLastMsgSeq(session.getLastMsgSeq())
                .setLastMsgTime(session.getLastMsgTime())
                .setShowTop(member.isShowTop())
                .setSilence(member.isSilence())
                .setHidden(member.isHidden())
                .setAtList(member.getAtList());
    }

    /** 群聊：title/avatar 取群的标题、头像。 */
    public static LinkChatSessionVo createGroupVo(ChatSession session, ChatSessionMember member, GroupInfo group) {
        LinkChatSessionVo vo = new LinkChatSessionVo();
        return vo.setChatId(session.getChatId())
                .setCategory(session.getCategory())
                .setType(session.getType())
                .setTitle(group.getTitle())
                .setActive(member.isActive())
                .setAvatar(group.getAvatar())
                .setLastMsgType(session.getLastMsgType())
                .setOwnerId(member.getOwnerId().toHexString())
                .setTargetId(member.getTargetId().toHexString())
                .setLastMsgSummary(session.getLastMsgSummary())
                .setUnreadCount(session.getLastMsgSeq() - member.getLastReadSeq())
                .setLastReadSeq(member.getLastReadSeq())
                .setLastMsgSeq(session.getLastMsgSeq())
                .setLastMsgTime(session.getLastMsgTime())
                .setShowTop(member.isShowTop())
                .setSilence(member.isSilence())
                .setHidden(member.isHidden())
                .setAtList(member.getAtList());
    }

    public static LinkChatSessionVo fromVo(ChatSession session) {
        LinkChatSessionVo vo = new LinkChatSessionVo();
//        vo.set
    }
}
