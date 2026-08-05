package com.link.restapi.module.trtc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * TRTC 应用配置。
 *
 * <p><b>SecretKey 绝不能写死在代码里</b>，更不能出现在前端。
 * 建议通过环境变量注入（见 application.yml 里的 {@code ${TRTC_SECRET_KEY:}}），
 * 这样密钥不进代码仓库。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月31日
 */
@Data
@Component
@ConfigurationProperties(prefix = "link.trtc")
public class TrtcProperties {

    /** 腾讯云应用 ID */
    private long sdkAppId;

    /** 应用密钥，只能存在于服务端。通过环境变量注入，勿写进配置文件提交到仓库 */
    private String secretKey;

    /**
     * UserSig 有效期（秒），默认 24 小时。
     *
     * <p>不宜过长：签发后无法撤销，用户被封禁/离职后凭证仍然有效，
     * 时长即风险窗口。也不宜过短：过期会导致通话中掉线，前端需要重新拉取。
     */
    private long expireSeconds = 24 * 60 * 60L;
}
