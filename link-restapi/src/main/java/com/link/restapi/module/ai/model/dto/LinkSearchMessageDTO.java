package com.link.restapi.module.ai.model.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 消息搜索 DTO
 * 支持按关键词、时间范围、发送者等条件搜索消息
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月27日
 */

@Data
@Accessors(chain = true)
public class LinkSearchMessageDTO {

    /**
     * 用户ID（必填）
     */
    private String userId;

    /**
     * 会话ID（必填）
     */
    private String chatId;

    /**
     * 会话类型（必填）
     * 1=单聊，2=群聊
     */
    private int type;

    /**
     * 返回消息数量限制，默认500
     */
    private int limit = 500;

    /**
     * 搜索关键词（可选）
     * 如果提供，则在消息内容中搜索包含该关键词的消息
     */
    private String keyword;

    /**
     * 开始时间（可选）
     * 毫秒时间戳
     */
    private Long startTime;

    /**
     * 结束时间（可选）
     * 毫秒时间戳
     */
    private Long endTime;

    /**
     * 发送者ID（可选）
     * 如果提供，则只返回该用户发送的消息
     */
    private String senderId;
}
