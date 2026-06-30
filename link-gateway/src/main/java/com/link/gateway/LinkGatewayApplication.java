package com.link.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 网关启动类。统一 HTTP 入口（默认 8000），经 Nacos 服务发现把请求路由到 link-restapi / link-oss。
 *
 * <p>reactive（webflux）栈，与下游 webmvc 业务进程各自独立。入口处统一做 JWT 鉴权
 * （见 {@code AuthGlobalFilter}）与跨域（见 application.yml 的 globalcors）。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月25日
 */
@EnableDiscoveryClient
@SpringBootApplication
public class LinkGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(LinkGatewayApplication.class, args);
    }
}
