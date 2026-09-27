package com.link.restapi.module.message.model.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 根据 chatId 查询消息的请求 DTO
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月27日
 */
@Data
@Accessors(chain = true)
public class LinkPullMessageByChatDTO {

    /**
     * 用户ID（必填）
     */
    private String userId;

    /**
     * 会话ID（必填）
     * 格式：single_{minUid}_{maxUid} 或 group_{groupId}
     */
    private String chatId;

    /**
     * 最多返回多少条消息，默认500
     */
    private int limit = 500;
}
