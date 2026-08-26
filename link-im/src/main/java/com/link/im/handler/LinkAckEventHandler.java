package com.link.im.handler;

import com.link.common.core.model.ack.LinkAck;
import com.link.common.core.event.EventType;
import com.link.core.event.handler.EventHandler;

import com.link.util.delivery.MessageRetryManager;

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

    /**
     * ACK 是控制面信号，只做一次 ConcurrentHashMap.remove + Timeout.cancel，
     * 纯内存微秒级，就地在 eventLoop 上执行，不进 IMExecutor。
     *
     * <p>原因和心跳同理：ACK 排在被消息灌满的共享队列尾部时，重发任务迟迟取消不掉，
     * 客户端明明已经确认收到，服务端仍会到点重发一遍，白白产生重复投递。
     *
     * <p>注意：这里往后<b>不要</b>加“把消息 state 置为 delivered”这类落库操作，
     * 那会把 Mongo 往返搬到 eventLoop 上，阻塞同一个 eventLoop 上的所有连接。
     * 真要做已送达状态，得把落库那段单独甩给线程池 / 异步批量攒着写。
     */
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
