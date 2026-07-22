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

        if (this.config.getProtocol() == Protocol.WEBSOCKET) {
            p.addLast(new HttpServerCodec());
            p.addLast(new HttpObjectAggregator(this.config.getWebsocketMaxFrameSize()));
            p.addLast(new WebSocketServerProtocolHandler(this.config.getWebsocketPath()));
            p.addLast(new WebSocketFrameAdapter());
        }
        p.addLast(new ConnectionGuardHandler(this.config));
        p.addLast(new IdleStateHandler(this.config.getReaderIdleTime(),this.config.getWriterIdleTime(), this.config.getAllIdleTime()));
        p.addLast(new IdleHandler(this.config));
        p.addLast(new TcpInboundHandler(this.config, this.linkEventDispatcher, this.linkPackDataDecoder));
        p.addLast(new TcpOutboundHandler(this.linkPackDataEncoder));
    }
}
