package com.link.core.event.handler;

import com.link.core.event.EventType;
import io.netty.channel.Channel;

public interface EventHandler {

    EventType event();


    Class<?> bodyClass();

    public void handler(Object obj, Channel channel);

}
