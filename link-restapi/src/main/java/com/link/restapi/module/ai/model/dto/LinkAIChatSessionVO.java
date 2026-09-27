package com.link.restapi.module.ai.model.dto;

import com.link.base.entity.chat.ChatSessionMember;
import com.link.base.entity.group.GroupInfo;
import com.link.base.entity.user.UserInfo;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月27日
 */
@Data
@Accessors(chain = true)
public class LinkAIChatSessionVO {

    private String chatId;

    // 1单聊 2群聊
    private int type;

    private String title;

    private String remark;

    // 人数
    private int count;

    private long createTime;

    public static LinkAIChatSessionVO fromDefaultVo(ChatSessionMember member, UserInfo user) {
        LinkAIChatSessionVO vo = new LinkAIChatSessionVO();
        vo.setChatId(member.getChatId())
                .setType(member.getType())
                .setTitle(user.getNickname())
                .setCount(1)
                .setCreateTime(member.getCreatedTime());
        return vo;

    }

    public static LinkAIChatSessionVO fromGroupVo(ChatSessionMember member, GroupInfo group) {
        LinkAIChatSessionVO vo = new LinkAIChatSessionVO();
        vo.setChatId(member.getChatId())
                .setType(member.getType())
                .setTitle(group.getTitle())
                .setCount(group.getGroupMemberSize())
                .setCreateTime(member.getCreatedTime());
        return vo;
    }
}
