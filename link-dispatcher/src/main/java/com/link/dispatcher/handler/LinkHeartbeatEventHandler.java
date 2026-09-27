package com.link.dispatcher.handler;

import com.link.common.channel.DefaultChannelAttributeKeys;
import com.link.common.core.model.heart.LinkPing;
import com.link.common.core.model.heart.LinkPong;
import com.link.core.config.LinkCoreConfig;
import com.link.common.core.event.EventType;
import com.link.core.event.handler.EventHandler;

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

    /**
     * 心跳是存活探针，绝不能和消息重活挤 IMExecutor 那条共享队列：
     * 队列被消息灌满时心跳排不上，lastHeartbeatTime 停在旧值，
     * IdleHandler 却在没被阻塞的 eventLoop 上准点触发，把在线用户误判掉线。
     * 这里只有一次时间戳写入 + 一次 writeAndFlush，就地在 eventLoop 上跑最快也最准。
     */
    @Override
    public boolean inlineOnEventLoop() {
        return true;
    }

    @Override
    public void handler(Object obj, Channel channel) {
        LinkSession session = (LinkSession) channel.attr(AttributeKey.valueOf(DefaultChannelAttributeKeys.SESSION)).get();
        if (session == null) {
            return;
        }
        session.setLastHeartbeatTime(System.currentTimeMillis());
        LinkPong pong = new LinkPong();
        this.config.getLinkSender().send(EventType.HEARTBEAT,channel,pong);
    }

}
