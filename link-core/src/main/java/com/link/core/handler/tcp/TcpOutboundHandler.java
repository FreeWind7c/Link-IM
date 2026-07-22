package com.link.core.handler.tcp;

import com.link.core.codec.LinkPackDataEncoder;
import com.link.common.core.model.data.PackData;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月17日
 */
public class TcpOutboundHandler extends MessageToByteEncoder<PackData> {

    private final LinkPackDataEncoder linkPackDataEncoder;

    public TcpOutboundHandler(LinkPackDataEncoder linkPackDataEncoder) {
        this.linkPackDataEncoder = linkPackDataEncoder;
    }

    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, PackData packData, ByteBuf byteBuf) throws Exception {
        this.linkPackDataEncoder.encode(packData,byteBuf);
    }
}
