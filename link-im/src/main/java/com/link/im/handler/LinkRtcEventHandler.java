package com.link.im.handler;

import com.link.common.core.event.EventType;
import com.link.common.core.event.RTCEventType;
import com.link.common.core.model.call.LinkRtcCall;
import com.link.core.event.facotry.RTCEventHandlerFactory;
import com.link.core.event.handler.EventHandler;
import com.link.core.event.handler.RTCEventHandler;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月21日
 */
@Slf4j
@Component
public class LinkRtcEventHandler implements EventHandler {

    @Autowired
    private RTCEventHandlerFactory factory;

    @Override
    public EventType event() {
        return EventType.RTC_CALL;
    }

    @Override
    public Class<?> bodyClass() {
        return LinkRtcCall.class;
    }

    @Override
    public void handler(Object obj, Channel channel) {
        LinkRtcCall call = (LinkRtcCall) obj;
        RTCEventHandler eventHandler = factory.getEventHandler(RTCEventType.fromAction(call.getEventType()));
        if (eventHandler == null){
            log.error("找不到此RTC事件 -> {}",call.getEventType());
            return;
        }
        eventHandler.handler(call,channel);
    }
}
