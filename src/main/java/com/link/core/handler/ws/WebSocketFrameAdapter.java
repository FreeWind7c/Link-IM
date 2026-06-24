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
 * WebSocket 帧 <-> ByteBuf 适配器，使下游的 TCP 编解码与业务 handler 在 WS 模式下零改动复用。
 *
 * <p>入站：BinaryWebSocketFrame -> 取出 content() 的 ByteBuf 往下传，
 *        下游 TcpInboundHandler(ByteToMessageDecoder) 照常按长度字段切分 PackData。
 * <p>出站：上游 TcpOutboundHandler 编码出的 ByteBuf -> 包成 BinaryWebSocketFrame 再发出。
 *
 * <p>用二进制帧而非文本帧：PackData 是二进制协议(magic/action/length/body)，
 * TextWebSocketFrame 走 UTF-8 会破坏字节。
 */
public class WebSocketFrameAdapter extends ChannelDuplexHandler {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof WebSocketFrame) {
            // 只处理数据帧；控制帧(Ping/Pong/Close)由 WebSocketServerProtocolHandler 处理，不会到这。
            if (msg instanceof BinaryWebSocketFrame frame) {
                ByteBuf content = frame.content();
                content.retain();        // 帧随后会被释放，先 retain 再交给下游，由下游负责 release
                ctx.fireChannelRead(content);
            }
            // 非二进制帧(如误发的 TextWebSocketFrame)直接忽略并释放
            ((WebSocketFrame) msg).release();
            return;
        }
        // 握手阶段的 HTTP 等其它消息透传
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
