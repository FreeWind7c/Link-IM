package com.link.restapi.module.trtc.model.vo;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * TRTC 登录凭证，返回给前端用于 TUICallKitAPI.init()。
 *
 * <p>注意这里<b>不包含 SecretKey</b>——前端只需要签好的 userSig。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月31日
 */
@Data
@Accessors(chain = true)
public class LinkTrtcUserSigVo {

    /** 腾讯云应用 ID */
    private long sdkAppId;

    /** 凭证所属用户，前端会与本端登录用户比对，防止拿错身份 */
    private String userId;

    /** 登录签名 */
    private String userSig;

    /** 过期时间戳（毫秒），前端据此提前刷新 */
    private long expireAt;
}
