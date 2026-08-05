package com.link.restapi.module.user.model.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月01日
 */
@Data
@Accessors(chain = true)
public class LinkUserReportTokenDto {
    private String userId;

    private String token;

    private Short platform;
}
