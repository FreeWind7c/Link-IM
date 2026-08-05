package com.link.restapi.module.message.model.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月23日
 */
@Data
@Accessors(chain = true)
public class LinkPullMessageDto {
    private String chatId;

    private String userId;

    private int sessionType;

    private int skip;

    private int limit;
}
