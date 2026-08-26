package com.link.core.event.dispatcher;

import com.link.common.channel.DefaultChannelAttributeKeys;
import com.link.common.core.event.EventType;
import com.link.common.core.model.ack.LinkAck;
import com.link.core.config.LinkCoreConfig;
import com.link.core.event.facotry.EventHandlerFactory;
import com.link.core.event.handler.EventHandler;
import com.link.core.pool.thread.PartitionedOrderedExecutor;
import com.link.core.session.service.LinkSession;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月17日
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class LinkEventDispatcher {

    private final EventHandlerFactory factory;

    private final LinkCoreConfig config;

    @Autowired
    @Qualifier("IMExecutor")
    private ThreadPoolTaskExecutor workerPool;

    @Autowired
    private PartitionedOrderedExecutor orderedExecutor;


    public void eventDispatcher(EventType eventType, ByteBuf buf, Channel channel){
        // 门禁：未认证连接只放行 LOGIN，其余事件一律拒绝并关连接
        LinkSession session = (LinkSession) channel.attr(AttributeKey.valueOf(DefaultChannelAttributeKeys.SESSION)).get();
        if (!this.config.getConnectionSecurityManager().isAuthorized(eventType, session)) {
            log.warn("未认证连接尝试 {} 事件，关闭连接 {}", eventType, channel.id());
            channel.close();
            return;
        }
        EventHandler eventHandler = this.factory.getEventHandler(eventType);
        if (eventHandler == null)
            return;
//            throw new RuntimeException("未知事件:"+ eventType.getAction());
        // ByteBuf → byte[]：序列化器只认字节数组（已与 netty 解耦），这里在接入层完成读取
        byte[] body = new byte[buf.readableBytes()];
        buf.readBytes(body);
        Object data = this.config.getLinkSerializer().deserialize(body, eventHandler.bodyClass());

        // 控制面事件（心跳 / ACK）：纯内存、微秒级，就地在当前 eventLoop 上执行完，不进任何线程池。
        // 走 IMExecutor 的话，消息洪水灌满这条全用户共享的 FIFO 后，心跳/ACK 排在队尾迟迟跑不到，
        // 而 IdleStateHandler 在没被阻塞的 eventLoop 上准点触发，读到过期的 lastHeartbeatTime，
        // 就把正在正常发心跳的在线用户判成掉线踢掉（见 EventHandler#inlineOnEventLoop）。
        if (eventHandler.inlineOnEventLoop()) {
            try {
                eventHandler.handler(data, channel);
            } catch (Throwable t) {
                // 单帧处理失败不能顺着 pipeline 冒到 exceptionCaught 把连接关掉
                log.error("内联处理 {} 事件失败, channel={}", eventType, channel.id(), t);
            }
            return;
        }

        String partitionKey = eventHandler.partitionKey(data);
        if (partitionKey != null) {
            boolean accepted = this.orderedExecutor.submit(partitionKey, () -> {
                eventHandler.handler(data, channel);
            });
            if (!accepted) {
                log.error("分区积压已满，丢弃 {} 事件待客户端重发, key={}, channel={}",
                        eventType, partitionKey, channel.id());
            }
        } else {
            try {
                workerPool.submit(() -> {
                    eventHandler.handler(data, channel);
                });
            } catch (java.util.concurrent.RejectedExecutionException e) {
                log.error("IM线程池过载，丢弃 {} 事件, channel={}", eventType, channel.id());
            }
        }
    }
}
