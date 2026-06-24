package com.link.im.handler;

import com.link.common.channel.DefaultChannelAttributeKeys;
import com.link.core.config.LinkCoreConfig;
import com.link.common.core.event.EventType;
import com.link.core.event.handler.EventHandler;
import com.link.core.model.heart.LinkPing;
import com.link.core.model.heart.LinkPong;
import com.link.core.session.service.LinkSession;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月17日
 */
@Component
@RequiredArgsConstructor
public class LinkHeartbeatEventHandler implements EventHandler {

    private final LinkCoreConfig config;

    @Override
    public EventType event() {
        return EventType.HEARTBEAT;
    }

    @Override
    public Class<?> bodyClass() {
        return LinkPing.class;
    }

    @Override
    public void handler(Object obj, Channel channel) {
        LinkPing ping = (LinkPing) obj;
        LinkSession session = (LinkSession) channel.attr(AttributeKey.valueOf(DefaultChannelAttributeKeys.SESSION)).get();
        session.setLastHeartbeatTime(System.currentTimeMillis());
        LinkPong pong = new LinkPong();
        this.config.getLinkSender().send(EventType.HEARTBEAT,channel,pong);
    }

}
