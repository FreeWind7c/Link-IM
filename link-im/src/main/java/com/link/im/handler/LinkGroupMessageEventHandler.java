package com.link.im.handler;

import com.link.common.core.event.EventType;
import com.link.core.event.handler.EventHandler;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月18日
 */
@Slf4j
@Component
public class LinkGroupMessageEventHandler implements EventHandler {
    @Override
    public EventType event() {
        return EventType.GROUP_MESSAGE;
    }

    @Override
    public Class<?> bodyClass() {
        return null;
    }

    @Override
    public void handler(Object obj, Channel channel) {

    }
}
