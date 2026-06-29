package com.link.core.server;

import com.link.core.codec.LinkPackDataDecoder;
import com.link.core.codec.LinkPackDataEncoder;
import com.link.core.config.LinkCoreConfig;
import com.link.common.core.event.EventType;
import com.link.core.event.dispatcher.LinkEventDispatcher;
import com.link.core.handler.TcpHandlerInitializer;
import com.link.core.session.facotry.DefaultChannelSessionFactory;
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

import java.util.Arrays;

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
        bootstrap.group(bossGroup,workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_REUSEADDR,this.config.isSoReuseAddr())
                .option(ChannelOption.SO_SNDBUF,this.config.getSoSndBuf())
                .option(ChannelOption.SO_RCVBUF,this.config.getSoRcvBuf())
                .option(ChannelOption.TCP_NODELAY,this.config.isTcpNoDelay())
                .localAddress(this.config.getPort())
                .childHandler(new TcpHandlerInitializer(this.config, this.linkEventDispatcher, this.linkPackDataDecoder, this.linkPackDataEncoder));
    }

    @PreDestroy
    public void shutdown() {
        if (bossGroup != null)   bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
    }


    @Override
    public void run(ApplicationArguments args) throws Exception {
        init();
        ChannelFuture future = bootstrap.bind().sync();
        log.info("Link IM Server started on port " + config.getPort());

    }
}
