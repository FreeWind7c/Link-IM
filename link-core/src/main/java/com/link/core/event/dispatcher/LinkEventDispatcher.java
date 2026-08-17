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
            throw new RuntimeException("未知事件:"+ eventType.getAction());
        // ByteBuf → byte[]：序列化器只认字节数组（已与 netty 解耦），这里在接入层完成读取
        byte[] body = new byte[buf.readableBytes()];
        buf.readBytes(body);
        String jsonStr = new String(body, java.nio.charset.StandardCharsets.UTF_8);
        Object data = this.config.getLinkSerializer().deserialize(body, eventHandler.bodyClass());
        String partitionKey = eventHandler.partitionKey(data);
        if (partitionKey != null) {
            this.orderedExecutor.submit(partitionKey, () -> {
                eventHandler.handler(data, channel);
            });
        } else {
            workerPool.submit(() -> {
                eventHandler.handler(data, channel);
            });
        }
    }
}
