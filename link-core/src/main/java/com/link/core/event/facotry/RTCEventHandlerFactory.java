package com.link.core.event.facotry;

import com.link.common.core.event.EventType;
import com.link.common.core.event.RTCEventType;
import com.link.core.event.handler.EventHandler;
import com.link.core.event.handler.RTCEventHandler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月21日
 */
@Component
public class RTCEventHandlerFactory {

    private final Map<RTCEventType, RTCEventHandler> eventGroup = new ConcurrentHashMap<>();

    public RTCEventHandlerFactory(List<RTCEventHandler> handlers){
        for (RTCEventHandler handler : handlers) {
            eventGroup.put(handler.event(),handler);
        }
    }

    public RTCEventHandler getEventHandler(RTCEventType eventType){
        return this.eventGroup.get(eventType);
    }

}
