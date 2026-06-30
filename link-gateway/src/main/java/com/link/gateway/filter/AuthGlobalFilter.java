package com.link.gateway.filter;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.link.common.util.TokenUtil;
import com.link.gateway.config.GatewayAuthProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关层 JWT 全局鉴权。
 *
 * <p>所有进入网关的请求先过这里：白名单路径（登录/注册等）直接放行；其余请求必须携带
 * 合法 {@code Authorization: Bearer <token>}，校验通过后把身份（userId / account / platform）
 * 写入请求头透传给下游，下游无需再解析 JWT。校验失败统一返回 401。
 *
 * <p>身份头在入口处强制剥离重写，杜绝客户端自带伪造的 X-User-* 头穿透到下游。
 */
@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    /** 透传给下游的身份头，与下游约定一致 */
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_ACCOUNT = "X-User-Account";
    public static final String HEADER_PLATFORM = "X-User-Platform";

    private static final String BEARER_PREFIX = "Bearer ";

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final GatewayAuthProperties authProperties;

    public AuthGlobalFilter(GatewayAuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // CORS 预检（OPTIONS）不带 token，直接放行交给 CORS 处理，否则会被 401
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return chain.filter(exchange);
        }

        String path = request.getURI().getPath();

        // 白名单（登录/注册等无 token 入口）直接放行
        for (String pattern : authProperties.getWhitelist()) {
            if (pathMatcher.match(pattern, path)) {
                return chain.filter(exchange);
            }
        }

        // 取 token：Authorization: Bearer xxx
        String auth = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange, "缺少 Authorization Bearer token");
        }
        String token = auth.substring(BEARER_PREFIX.length()).trim();

        DecodedJWT jwt;
        try {
            jwt = TokenUtil.verify(token);
        } catch (JWTVerificationException e) {
            return unauthorized(exchange, "token 非法或已过期: " + e.getMessage());
        }

        String userId = jwt.getClaim("userId").asString();
        String account = jwt.getClaim("account").asString();
        String platform = jwt.getClaim("platform").asString();
        if (userId == null) {
            return unauthorized(exchange, "token 缺少 userId 声明");
        }

        // 身份一律以校验通过的 token 为准；先清掉客户端自带的同名头再写入，防伪造穿透
        ServerHttpRequest mutated = request.mutate()
                .headers(h -> {
                    h.remove(HEADER_USER_ID);
                    h.remove(HEADER_ACCOUNT);
                    h.remove(HEADER_PLATFORM);
                    h.add(HEADER_USER_ID, userId);
                    if (account != null) h.add(HEADER_ACCOUNT, account);
                    if (platform != null) h.add(HEADER_PLATFORM, platform);
                })
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String reason) {
        log.warn("网关鉴权失败 [{} {}]: {}",
                exchange.getRequest().getMethod(), exchange.getRequest().getURI().getPath(), reason);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        // 尽量靠前执行，先鉴权再路由
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
