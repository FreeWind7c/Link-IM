package com.link.restapi.module.user.model.dto;

import com.link.im.util.MD5Util;
import com.link.im.entity.user.UserInfo;

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

    /** 由注册 DTO 构建用户实体。DTO 在 api 层，可依赖 link-im 实体，方向正确。 */
    public UserInfo toUserInfo() {
        return new UserInfo()
                .setAccount(this.account)
                .setPassword(MD5Util.encrypt(this.password))
                .setRegisterTime(System.currentTimeMillis())
                .setNickname("用户" + this.account);
    }
}
