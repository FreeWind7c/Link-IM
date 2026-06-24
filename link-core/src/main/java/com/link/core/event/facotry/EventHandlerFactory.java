package com.link.core.event.facotry;


import com.link.common.core.event.EventType;
import com.link.core.event.handler.EventHandler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月17日
 */
@Component
public class EventHandlerFactory {

    private final Map<EventType, EventHandler> eventGroup = new ConcurrentHashMap<>();


    public EventHandlerFactory(List<EventHandler> handlers){
        for (EventHandler handler : handlers) {
            eventGroup.put(handler.event(),handler);
        }
    }


    public EventHandler getEventHandler(EventType eventType){
        return this.eventGroup.get(eventType);
    }

}
