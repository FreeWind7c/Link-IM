package com.link.restapi.module.trtc.model.dto;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.redis.core.index.PathBasedRedisIndexDefinition;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月16日
 */
@Data
@Accessors(chain = true)
public class LinkTrtcRoomIdDTO {
    private String sndId;

    private String sndName;

    private String rcvId;

    private String chatId;

    private String messageId;

    // 1单聊 2群聊
    private int type;

    private int mediaType;
}
