package com.link.restapi.module.message.model.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月26日
 */
@Data
@Accessors(chain = true)
public class LinkCompleteMessageDto {

    private String chatId;

    /** 拉取者 uid。群聊必填：用于按其 blackoutGaps 过滤被踢期间不可见的消息。 */
    private String userId;

    private int sessionType;

    private int from;

    private int to;

}
