package com.link.restapi.module.ai.model.vo;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 会话解析结果 VO
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月27日
 */
@Data
@Accessors(chain = true)
public class LinkResolvedChatSessionVO {

    /**
     * 会话ID（chatId）
     * 单聊格式：single_{minUid}_{maxUid}
     * 群聊格式：group_{groupId}
     */
    private String chatId;

    /**
     * 会话类型
     * 1=单聊，2=群聊
     */
    private int sessionType;

    /**
     * 会话显示名称
     * 单聊：对方昵称或备注
     * 群聊：群名或群备注
     */
    private String displayName;

    /**
     * 目标ID
     * 单聊：对方用户ID
     * 群聊：群ID
     */
    private String targetId;

    /**
     * 匹配方式说明（调试用）
     * 例如："通过用户昵称匹配" / "通过好友备注匹配" / "通过群名匹配" / "通过群备注匹配"
     */
    private String matchedBy;
}
