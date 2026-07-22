package com.link.core.codec;

import com.link.common.core.model.data.PackData;
import com.link.core.config.LinkCoreConfig;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;



/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月18日
 */
@Slf4j
@Component

public class LinkPackDataDecoder {

    @Autowired
    private LinkCoreConfig config;

    public PackData decoder(Channel channel, ByteBuf byteBuf){

        // 连协议头都没收齐，返回 null 等待更多数据（这是半包，不是错误）
        if (byteBuf.readableBytes() < LinkCoreConfig.PROTO_FRAME_LENGTH)
            return null;

        byteBuf.markReaderIndex();
        short magic = byteBuf.readShort();
        if (magic != this.config.getMagic()){
            byteBuf.resetReaderIndex();
            channel.close();
            throw new RuntimeException("channelId="+ channel.id()+"魔数校验失败，关闭连接");
        }

        short action = byteBuf.readShort();
        int len = byteBuf.readInt();
        // 帧长度校验：非法/超大 length 直接关连接，防止累积缓冲被撑爆
        if (!this.config.getConnectionSecurityManager().isFrameLengthValid(len)) {
            byteBuf.resetReaderIndex();
            channel.close();
            throw new RuntimeException("channelId=" + channel.id() + " 非法帧长度=" + len + "，关闭连接");
        }
        // body 还没收齐：回退到帧首，返回 null 等下次（不能返回残包）
        if (byteBuf.readableBytes() < len) {
            byteBuf.resetReaderIndex();
            return null;
        }
        return new PackData(magic,action,len,new byte[0]);
    }
}
