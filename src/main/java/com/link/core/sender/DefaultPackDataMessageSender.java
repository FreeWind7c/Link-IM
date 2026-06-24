package com.link.core.sender;

import com.link.core.config.LinkCoreConfig;
import com.link.core.event.EventType;
import com.link.core.model.data.PackData;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.util.List;


/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月17日
 */

public class DefaultPackDataMessageSender implements LinkMessageSender {

    private final LinkCoreConfig config;


    public DefaultPackDataMessageSender(LinkCoreConfig config){
        this.config = config;
    }

    @Override
    public void send( EventType event,Channel channel, Object obj){
        if (channel == null || !channel.isActive()) {
            return;
        }
        byte[] body = this.config.getLinkSerializer().serialize(obj);
        PackData packData = new PackData(event.getAction(), body.length, body);
        channel.writeAndFlush(packData);
    }

    @Override
    public void send(EventType event, List<Channel> channels, Object obj) {
        if (channels == null || channels.isEmpty()) {
            return;
        }

        byte[] body = this.config.getLinkSerializer().serialize(obj);
        PackData packData = new PackData(event.getAction(), body.length, body);

        // ChannelGroup 负责逐个写出并自动忽略已关闭的 channel，返回聚合 future。
        ChannelGroup group = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        group.addAll(channels);
        group.writeAndFlush(packData);
    }

}

