package com.link.core.handler.in;

import com.link.common.channel.DefaultChannelAttributeKeys;
import com.link.core.codec.LinkPackDataDecoder;
import com.link.core.config.LinkCoreConfig;
import com.link.core.event.EventType;
import com.link.core.event.dispatcher.LinkEventDispatcher;
import com.link.core.model.data.PackData;
import com.link.core.session.service.LinkSession;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月15日
 */
@Slf4j
public class TcpInboundHandler extends ByteToMessageDecoder {

    // ByteToMessageDecoder 持有 per-channel 累积缓冲，是有状态的，
    // 不能用 @Sharable 单例共享，必须每条连接 new 一个。
    // 因此它的依赖只能走构造器注入，不能用 @Autowired（new 出来的实例 Spring 注入不进去）。
    private final LinkCoreConfig config;

    private final LinkEventDispatcher linkEventDispatcher;

    private final LinkPackDataDecoder linkPackDataDecoder;

    protected AttributeKey<LinkSession> sessAttributeKey = AttributeKey.valueOf(DefaultChannelAttributeKeys.SESSION);

    public TcpInboundHandler(LinkCoreConfig config,
                             LinkEventDispatcher linkEventDispatcher,
                             LinkPackDataDecoder linkPackDataDecoder) {
        this.config = config;
        this.linkEventDispatcher = linkEventDispatcher;
        this.linkPackDataDecoder = linkPackDataDecoder;
    }


    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, List<Object> list) throws Exception {
        Channel channel = channelHandlerContext.channel();
        PackData packData = this.linkPackDataDecoder.decoder(channel, byteBuf);
        // 半包：数据还没收齐，直接返回等下次 decode
        if (packData == null) {
            return;
        }
        EventType eventType = EventType.fromAction(packData.getAction());
        if (eventType == null) {
            // 未知 action：跳过 body 后丢弃该帧
            byteBuf.skipBytes(packData.getLength());
            throw new RuntimeException("channelId=" + channel.id() + " 未知 action=" + packData.getAction() + "，丢弃该帧");
        }
        ByteBuf buf = byteBuf.readSlice(packData.getLength());
        linkEventDispatcher.eventDispatcher(eventType, buf, channel);
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        if (channel != null) {
            this.config.getSessionFactory().channelRegister(channel);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        super.channelUnregistered(ctx);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        super.channelWritabilityChanged(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        super.userEventTriggered(ctx, evt);
    }

}
