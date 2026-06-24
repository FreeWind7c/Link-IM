package com.link.core.session.facotry;

import io.netty.channel.Channel;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月12日
 */
public interface LinkSessionFactory {

    public void channelRegister(Channel channel);

}
