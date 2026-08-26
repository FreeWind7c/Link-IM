package com.link.restapi.module.message.model.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月26日
 */
@Data
@Accessors(chain = true)
public class LinkAIPullMessageDTO {

    private String userId;

    private int limit;

    // 会话类型 1单聊 2群聊
    private int sessionType;

    private long startTime;

    private long endTime;


}
