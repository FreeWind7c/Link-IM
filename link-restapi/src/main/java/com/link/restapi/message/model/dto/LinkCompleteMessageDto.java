package com.link.restapi.message.model.dto;

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

    private int sessionType;

    private int from;

    private int to;

}
