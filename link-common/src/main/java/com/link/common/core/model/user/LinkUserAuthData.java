package com.link.common.core.model.user;

import lombok.Data;
import lombok.ToString;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月18日
 */
@ToString
@Data
public class LinkUserAuthData {

    private short action;

    private String userId;

    private String token;

    // 0桌面 1手机 2平板
    private short platform;

}
