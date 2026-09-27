package com.link.core.server;

import com.link.core.codec.LinkPackDataDecoder;
import com.link.core.codec.LinkPackDataEncoder;
import com.link.core.config.LinkCoreConfig;
import com.link.core.event.dispatcher.LinkEventDispatcher;
import com.link.core.handler.security.ConnectionRateLimitHandler;
import com.link.core.handler.TcpHandlerInitializer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月10日
 *
 * Netty 接入服务。仅在显式开启 link.netty.enabled=true 的进程中加载启动，
 * 使长连接接入（gateway 进程）与 HTTP 业务（api 进程）可分进程独立部署：
 * gateway 开启本 Bean 跑 Netty；api 不开启，本 Bean 不注册，进程内不绑端口。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "link.netty", name = "enabled", havingValue = "true", matchIfMissing = false)
public class DefaultServer implements ApplicationRunner {

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ServerBootstrap bootstrap;

    @Autowired
    private LinkCoreConfig config;

    @Autowired
    private LinkEventDispatcher linkEventDispatcher;

    @Autowired
    private LinkPackDataDecoder linkPackDataDecoder;

    @Autowired
    private LinkPackDataEncoder linkPackDataEncoder;

    @Autowired
    private GracefulShutdownService gracefulShutdownService;

    private ChannelFuture serverChannelFuture;

    private ConnectionRateLimitHandler connectionRateLimitHandler;


    public void init(){
        if (config == null){
            this.config = new LinkCoreConfig();
        }
        if(bootstrap == null){
            bossGroup = new NioEventLoopGroup(this.config.getBossGroupThreadCore());
        }
        if (workerGroup == null){
            workerGroup = new NioEventLoopGroup(this.config.getWorkerGroupThreadCore());
        }
        if (bootstrap == null){
            bootstrap = new ServerBootstrap();
            initSeverBootStrap(bossGroup,workerGroup,bootstrap);
        }
    }


    private void initSeverBootStrap(EventLoopGroup bossGroup, EventLoopGroup workerGroup, ServerBootstrap bootstrap) {

        // 🔑 创建连接限流器（@Sharable，全局单例）
        if (config.isConnectionRateLimitEnabled() && config.getConnectionRateLimitPerSecond() > 0) {
            connectionRateLimitHandler = new ConnectionRateLimitHandler(
                config.getConnectionRateLimitPerSecond(),
                config.isConnectionRateLimitBlock(),
                config.getConnectionRateLimitMaxWait()
            );
            log.info("连接限流已启用：每秒 {} 个连接", config.getConnectionRateLimitPerSecond());
        } else {
            log.warn("连接限流未启用，高并发重连可能导致服务雪崩");
        }

        bootstrap.group(bossGroup,workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_REUSEADDR,this.config.isSoReuseAddr())
                .option(ChannelOption.SO_SNDBUF,this.config.getSoSndBuf())
                .option(ChannelOption.SO_RCVBUF,this.config.getSoRcvBuf())
                .option(ChannelOption.TCP_NODELAY,this.config.isTcpNoDelay())
                .localAddress(this.config.getPort())
                .childHandler(new TcpHandlerInitializer(
                    this.config,
                    this.linkEventDispatcher,
                    this.linkPackDataDecoder,
                    this.linkPackDataEncoder,
                    this.connectionRateLimitHandler  // 传入限流器
                ));
    }

    @PreDestroy
    public void shutdown() {

        try {
            // 1. 停止接受新连接
            if (serverChannelFuture != null && serverChannelFuture.channel().isActive()) {
                log.info("停止接受新连接...");
                serverChannelFuture.channel().close().sync();
            }

            // 2. 通知所有在线用户并等待自然断开
            if (gracefulShutdownService != null) {
                log.info("通知用户下线");
                gracefulShutdownService.performGracefulShutdown();
            }

        } catch (Exception e) {
            log.error("优雅停服过程异常", e);
        } finally {
            // 3. 关闭 Netty 线程池
            if (bossGroup != null)   bossGroup.shutdownGracefully();
            if (workerGroup != null) workerGroup.shutdownGracefully();
        }
    }



    @Override
    public void run(ApplicationArguments args) throws Exception {
        init();
        serverChannelFuture = bootstrap.bind().sync();
        log.info("Link IM Server started on port " + config.getPort());

    }
}
