package com.link.register.server.grpc;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * gRPC Server 的生命周期管理 —— 用 Spring 的 {@link SmartLifecycle} 把 gRPC Server
 * 挂到 Spring 容器的启动/停机上：容器启动时拉起 gRPC 端口，容器关闭时优雅停机。
 *
 * <p>为什么用 grpc-netty-shaded 的 {@link NettyServerBuilder}？它自带一份 shaded 过的 Netty，
 * 与本工程 SB4 的 Netty 4.2 完全隔离（包名被重定位到 io.grpc.netty.shaded.*），互不冲突。
 *
 * <p>keepalive 配置：这是「连接层保活」，即之前说的「连接断=秒级下线」的底层机制。
 * 服务端定期发 HTTP/2 PING 探活，长时间没响应就主动断连，触发订阅流的 onCancel 清理。
 */
@Component
public class GrpcServerBootstrap implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(GrpcServerBootstrap.class);

    private final RegistryServiceImpl registryService;

    /** gRPC 监听端口，默认 9090，可用 link.register.server.port 覆盖 */
    @Value("${link.register.server.port:9500}")
    private int port;

    private Server server;
    private volatile boolean running = false;

    public GrpcServerBootstrap(RegistryServiceImpl registryService) {
        this.registryService = registryService;
    }

    @Override
    public void start() {
        try {
            server = NettyServerBuilder.forPort(port)
                    .addService(registryService)
                    // 连接层保活：每 30s 无数据就发 PING；对端 10s 内不回则判定连接失效
                    .keepAliveTime(30, TimeUnit.SECONDS)
                    .keepAliveTimeout(10, TimeUnit.SECONDS)
                    // 允许客户端发起 keepalive，且无活跃调用时也允许（否则客户端 PING 会被拒）
                    .permitKeepAliveWithoutCalls(true)
                    .permitKeepAliveTime(10, TimeUnit.SECONDS)
                    .build()
                    .start();
            running = true;
            log.info("注册中心 gRPC Server 已启动，监听端口 {}", port);
        } catch (IOException e) {
            throw new IllegalStateException("gRPC Server 启动失败，端口 " + port, e);
        }
    }

    @Override
    public void stop() {
        if (server != null) {
            try {
                // 优雅停机：先停止接收新请求，等待在途请求处理完（最多 5s）
                server.shutdown().awaitTermination(5, TimeUnit.SECONDS);
                log.info("注册中心 gRPC Server 已停止");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                server.shutdownNow();
            }
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
