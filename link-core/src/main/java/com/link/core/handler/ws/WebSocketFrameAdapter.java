package com.link.core.handler.ws;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月18日
 *
 */
public class WebSocketFrameAdapter extends ChannelDuplexHandler {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof WebSocketFrame) {
            if (msg instanceof BinaryWebSocketFrame frame) {
                ByteBuf content = frame.content();
                content.retain();
                ctx.fireChannelRead(content);
            }
            ((WebSocketFrame) msg).release();
            return;
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof ByteBuf buf) {
            // 上游编码产物 ByteBuf 包成二进制帧
            ctx.write(new BinaryWebSocketFrame(buf), promise);
        } else {
            ctx.write(msg, promise);
        }
    }
}
