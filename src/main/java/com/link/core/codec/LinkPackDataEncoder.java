package com.link.core.codec;

import com.link.core.config.LinkCoreConfig;
import com.link.core.model.data.PackData;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月17日
 */
@Component
public class LinkPackDataEncoder {

    @Autowired
    private LinkCoreConfig config;

    public void encode(PackData packData,  ByteBuf buf){
        //TODO 后续做加密操作
        packData.setMagic(this.config.getMagic());
        buf.writeShort(packData.getMagic());
        buf.writeShort(packData.getAction());
        buf.writeInt(packData.getLength());
        buf.writeBytes(packData.getBody());
    }

}
