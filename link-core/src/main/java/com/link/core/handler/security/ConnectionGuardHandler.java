package com.link.core.handler.security;

import com.link.common.channel.DefaultChannelAttributeKeys;
import com.link.core.config.LinkCoreConfig;
import com.link.core.session.service.LinkSession;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.util.AttributeKey;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月18日
 */
public class ConnectionGuardHandler extends ChannelInboundHandlerAdapter {

    private final LinkCoreConfig config;

    public ConnectionGuardHandler(LinkCoreConfig config) {
        this.config = config;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // 超限：onConnect 内部已关闭连接，不再向后传播
        if (!config.getConnectionSecurityManager().onConnect(ctx)) {
            return;
        }
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // 连接计数扣减 + 取消认证超时
        this.config.getConnectionSecurityManager().onDisconnect(ctx.channel());
        LinkSession session = (LinkSession) ctx.channel().attr(AttributeKey.valueOf(DefaultChannelAttributeKeys.SESSION)).get();
        if (session != null) {
            this.config.getSessionManager().removeSession(session);
        }
        super.channelInactive(ctx);
    }
}
