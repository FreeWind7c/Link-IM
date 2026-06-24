package com.link.core.handler;

import com.link.core.codec.LinkPackDataDecoder;
import com.link.core.codec.LinkPackDataEncoder;
import com.link.core.config.LinkCoreConfig;
import com.link.core.config.Protocol;
import com.link.core.event.dispatcher.LinkEventDispatcher;
import com.link.core.handler.idle.IdleHandler;
import com.link.core.handler.tcp.TcpInboundHandler;
import com.link.core.handler.tcp.TcpOutboundHandler;
import com.link.core.handler.security.ConnectionGuardHandler;
import com.link.core.handler.ws.WebSocketFrameAdapter;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleStateHandler;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月11日
 */
public class TcpHandlerInitializer extends ChannelInitializer {

    private LinkCoreConfig config;

    // 这些是 Spring 单例 bean，由 DefaultServer 注入进来再传给每条连接的 handler，
    // 避免在 new 出来的 handler 里用 @Autowired（注入不进去会是 null）
    private final LinkEventDispatcher linkEventDispatcher;

    private final LinkPackDataDecoder linkPackDataDecoder;

    private final LinkPackDataEncoder linkPackDataEncoder;

    public TcpHandlerInitializer(LinkCoreConfig config,
                                 LinkEventDispatcher linkEventDispatcher,
                                 LinkPackDataDecoder linkPackDataDecoder,
                                 LinkPackDataEncoder linkPackDataEncoder){
        this.config = config;
        this.linkEventDispatcher = linkEventDispatcher;
        this.linkPackDataDecoder = linkPackDataDecoder;
        this.linkPackDataEncoder = linkPackDataEncoder;
    }


    @Override
    protected void initChannel(Channel channel) throws Exception {
        ChannelPipeline p = channel.pipeline();

        // WebSocket 模式：在业务 handler 前插入 HTTP 编解码 + 握手 + 帧适配器。
        // 适配器把 BinaryWebSocketFrame <-> ByteBuf 互转，使下面的 TCP 编解码/业务 handler 零改动复用。
        if (this.config.getProtocol() == Protocol.WEBSOCKET) {
            p.addLast(new HttpServerCodec());
            p.addLast(new HttpObjectAggregator(this.config.getWebsocketMaxFrameSize()));
            p.addLast(new WebSocketServerProtocolHandler(this.config.getWebsocketPath()));
            p.addLast(new WebSocketFrameAdapter());
        }
        // 以下为 TCP / WebSocket 共用的业务链路，顺序保持不变。
        p.addLast(new ConnectionGuardHandler(this.config));
        p.addLast(new IdleStateHandler(this.config.getReaderIdleTime(),this.config.getWriterIdleTime(), this.config.getAllIdleTime()));
        p.addLast(new IdleHandler(this.config));
        p.addLast(new TcpInboundHandler(this.config, this.linkEventDispatcher, this.linkPackDataDecoder));
        p.addLast(new TcpOutboundHandler(this.linkPackDataEncoder));
    }
}
