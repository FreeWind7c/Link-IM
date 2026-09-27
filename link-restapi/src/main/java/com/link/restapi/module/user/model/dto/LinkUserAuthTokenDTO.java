package com.link.restapi.module.user.model.dto;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.redis.core.index.PathBasedRedisIndexDefinition;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年09月04日
 */
@Data
@Accessors(chain = true)
public class LinkUserAuthTokenDTO {

    private String userId;

    private int platform;

    private String refreshToken;
}
