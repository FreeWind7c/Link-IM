package com.link.im.handler;

import com.link.common.channel.DefaultChannelAttributeKeys;

import com.link.common.core.model.ack.LinkAck;
import com.link.core.config.LinkCoreConfig;
import com.link.common.core.event.EventType;
import com.link.core.event.handler.EventHandler;

import com.link.core.session.service.LinkSession;
import com.link.im.mongo.BaseMongoService;
import com.link.util.delivery.MessageRetryManager;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
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

    @Autowired
    private LinkCoreConfig config;

    @Override
    public EventType event() {
        return EventType.ACK;
    }

    @Override
    public Class<?> bodyClass() {
        return LinkAck.class;
    }

    @Override
    public void handler(Object obj, Channel channel) {
        LinkSession session = (LinkSession) channel.attr(AttributeKey.valueOf(DefaultChannelAttributeKeys.SESSION)).get();
        LinkAck ack = (LinkAck) obj;
        this.retryManager.ack(ack.getId(),channel);


//        this.config.getLinkSender().send(EventType.ACK,channel,pong);
        // 按 messageId + 当前 channel 精确取消那一条的超时任务，不会误伤别条/别端。
//        this.retryManager.ack(ack.getId(), channel);
        // TODO 如需“已送达”状态：在此把库里该消息 state 置为 delivered
    }
}
