package com.link.restapi.module.ai.model.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * AI 根据会话名称解析 chatId 的请求 DTO
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月27日
 */
@Data
@Accessors(chain = true)
public class LinkResolveChatSessionDTO {

    /**
     * 当前用户ID（必填）
     * 用于查询该用户视角下的好友备注和群备注
     */
    private String userId;

    /**
     * 会话名称（必填）
     * 可能是：用户昵称、好友备注、群名、群备注
     */
    private String sessionName;

    /**
     * 会话类型（必填）
     * 1=单聊，2=群聊
     */
    private int sessionType;
}
