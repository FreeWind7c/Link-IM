package com.link.dispatcher.handler;

import com.link.common.core.model.ack.LinkAck;
import com.link.common.core.event.EventType;
import com.link.core.event.handler.EventHandler;


import com.link.core.util.delivery.MessageRetryManager;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月18日
 *
 * 处理 B->S 的 ACK：接收方确认收到某条消息后，取消该消息的超时重发任务。
 * 这是 S->C 可靠性闭环的“确认”一端，对应 processor 的“登记重发”一端。
 */
@Slf4j
@Component
public class LinkAckEventHandler  implements EventHandler {

    @Autowired
    private MessageRetryManager retryManager;

    @Override
    public EventType event() {
        return EventType.ACK;
    }

    @Override
    public Class<?> bodyClass() {
        return LinkAck.class;
    }

    @Override
    public boolean inlineOnEventLoop() {
        return true;
    }

    @Override
    public void handler(Object obj, Channel channel) {
        LinkAck ack = (LinkAck) obj;
        // 按 messageId + 当前 channel 精确取消那一条的超时任务，不会误伤别条/别端。
        this.retryManager.ack(ack.getId(), channel);
    }
}
