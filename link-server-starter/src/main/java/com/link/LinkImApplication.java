package com.link;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Netty 接入进程启动类。
 *
 * <p>本进程只跑长连接（Netty）：依赖 link-im（传递引入 link-core 引擎），classpath 上没有
 * webmvc，故不启动 HTTP。{@code link.netty.enabled=true}（见 application.yml）使
 * {@code DefaultServer} 生效并绑定 TCP/WebSocket 端口。
 *
 * <p>启动类置于 com.link 根包，使组件扫描覆盖 com.link.core / com.link.im 等全部模块。
 */
@SpringBootApplication
public class LinkImApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinkImApplication.class, args);
    }

}
