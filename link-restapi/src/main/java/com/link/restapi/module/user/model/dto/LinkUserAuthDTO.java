package com.link.restapi.module.user.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月20日
 */
@Data
@ToString
@AllArgsConstructor
public class LinkUserAuthDTO {

    private String account;

    private String password;

    private int platform;


}
