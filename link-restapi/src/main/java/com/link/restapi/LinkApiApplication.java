package com.link.restapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * HTTP 业务进程启动类。
 *
 * <p>本进程只跑 REST 业务：注册/登录、好友、会话、拉取历史消息等。classpath 上有
 * link-core 引擎类，但未设 {@code link.netty.enabled}，{@code DefaultServer} 不注册，
 * 故进程内不启动 Netty、不绑长连接端口。
 *
 * <p>注意：与长连接相关的两处能力在本进程内降级——登录时的“异地登录”校验拿不到在线
 * session（放行），好友通过后的在线推送为 no-op。两者需 Redis 在线表 + MQ 接入后跨进程生效。
 *
 * <p>启动类置于 com.link 根包，使组件扫描覆盖 com.link.restapi / com.link.im 等全部模块。
 */
@SpringBootApplication(scanBasePackages = "com.link")
public class LinkApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinkApiApplication.class, args);
    }

}
