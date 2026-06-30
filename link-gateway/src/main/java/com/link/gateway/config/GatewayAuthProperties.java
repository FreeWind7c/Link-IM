package com.link.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关鉴权配置。白名单路径（Ant 风格）无需携带 token，直接放行。
 *
 * <p>配置前缀 {@code link.gateway.auth}，默认放行登录/注册。可在 application.yml 覆盖。
 */
@Data
@Component
@ConfigurationProperties(prefix = "link.gateway.auth")
public class GatewayAuthProperties {

    /** 免鉴权白名单（Ant 路径模式），如 /api/user/auth、/api/user/register */
    private List<String> whitelist = new ArrayList<>();
}
