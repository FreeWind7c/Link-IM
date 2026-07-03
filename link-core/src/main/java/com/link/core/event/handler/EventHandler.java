package com.link.core.event.handler;

import com.link.common.core.event.EventType;
import io.netty.channel.Channel;

public interface EventHandler {

    EventType event();


    Class<?> bodyClass();

    public void handler(Object obj, Channel channel);

    /**
     * 分区键：需要顺序保证的事件（如聊天消息）返回其 chatId，
     * 分发器据此把同一会话的消息路由到同一个单线程分区，保证会话内严格有序。
     *
     * <p>返回 null（默认）表示该事件无顺序要求，走共享线程池并发处理。
     *
     * @param obj 已反序列化的消息体（bodyClass 类型）
     * @return 分区键，或 null
     */
    default String partitionKey(Object obj) {
        return null;
    }

}
