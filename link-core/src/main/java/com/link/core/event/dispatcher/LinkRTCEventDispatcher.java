package com.link.core.event.dispatcher;

import com.link.common.core.event.EventType;
import com.link.common.core.event.RTCEventType;
import com.link.core.event.handler.RTCEventHandler;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月21日
 */
@Component
public class LinkRTCEventDispatcher {

    private ConcurrentHashMap<EventType, RTCEventHandler> dispatcher = new ConcurrentHashMap<>();


}
