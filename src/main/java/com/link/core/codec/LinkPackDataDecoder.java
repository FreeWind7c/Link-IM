package com.link.core.codec;

import com.link.core.config.LinkCoreConfig;
import com.link.core.event.EventType;
import com.link.core.model.data.PackData;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPattern;



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

        // 此处 readerIndex 停在 body 起始位置，body 交给调用方 readSlice(len) 读取
        return new PackData(magic,action,len,new byte[0]);
    }
}
