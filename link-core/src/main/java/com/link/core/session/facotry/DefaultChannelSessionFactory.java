package com.link.core.session.facotry;

import com.link.common.channel.DefaultChannelAttributeKeys;

import com.link.core.config.LinkCoreConfig;
import com.link.core.session.DefaultChannelSession;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

import java.net.InetSocketAddress;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月16日
 */

public class DefaultChannelSessionFactory implements LinkSessionFactory{

    private LinkCoreConfig config;

    public DefaultChannelSessionFactory(LinkCoreConfig config) {
        this.config = config;

    }

    @Override
    public void channelRegister(Channel channel) {
        DefaultChannelSession session = new DefaultChannelSession(channel);
        InetSocketAddress remoteAddress = (InetSocketAddress) channel.remoteAddress();
        session.setHost(remoteAddress.getHostString());
        session.setPort(remoteAddress.getPort());
        channel.attr(AttributeKey.valueOf(DefaultChannelAttributeKeys.SESSION)).set(session);
    }
}
