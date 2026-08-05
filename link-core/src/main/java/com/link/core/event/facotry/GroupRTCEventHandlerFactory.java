package com.link.core.event.facotry;

import com.link.common.core.event.GroupRTCEventType;
import com.link.core.event.handler.GroupRTCEventHandler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 群通话同步事件处理器工厂。Spring 启动时把所有 {@link GroupRTCEventHandler}
 * 按其 event() 注册进来，分发时按 eventType 取。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月31日
 */
@Component
public class GroupRTCEventHandlerFactory {

    private final Map<GroupRTCEventType, GroupRTCEventHandler> eventGroup = new ConcurrentHashMap<>();

    public GroupRTCEventHandlerFactory(List<GroupRTCEventHandler> handlers) {
        for (GroupRTCEventHandler handler : handlers) {
            eventGroup.put(handler.event(), handler);
        }
    }

    public GroupRTCEventHandler getEventHandler(GroupRTCEventType eventType) {
        return this.eventGroup.get(eventType);
    }
}
