package com.link.restapi.module.trtc.model.dto;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.redis.core.index.PathBasedRedisIndexDefinition;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月18日
 */
@Data
@Accessors(chain = true)
public class LinkTrtcCancelCallDTO {

    private String chatId;

    private String roomId;
}
