package com.link.restapi.user.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月20日
 */
@Data
@AllArgsConstructor
public class LinkUserRegisterDTO {

    private String account;

    private String password;

    private String rePassword;

}
