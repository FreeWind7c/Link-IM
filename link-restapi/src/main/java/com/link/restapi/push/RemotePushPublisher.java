package com.link.restapi.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.link.common.core.event.EventType;
import com.link.common.core.mq.FanoutPushCommand;
import com.link.common.core.mq.DirectPushCommand;
import com.link.common.core.mq.PushMqConst;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 远程推送发布端。HTTP 进程不持长连接，无法直接 channel.write；本类把「请推给某用户」封成
 * {@link DirectPushCommand} 发到 fanout 交换机，由持连的 Netty 进程（link-consumer）消费后真正下发。
 *
 * <p>替代原先 restapi 直接调 LinkMessageSender 的做法——那条路在 HTTP 进程里 sessionMap 恒为空，
 * 推送必然丢失（参见本次改造的起因）。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月25日
 */
@Slf4j
@Component
public class RemotePushPublisher {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private ObjectMapper objectMapper = new ObjectMapper();


    /**
     * 批量发布在线推送指令（如群消息需推给多名成员）。payload 只序列化一次，逐个用户复用。
     *
     * @param userIds   目标用户集合
     * @param eventType 事件类型
     * @param payload   要推给端上的业务数据
     */
    public void push(EventType eventType, Collection<String> userIds,  Object payload) {
        if (CollectionUtils.isEmpty(userIds)) {
            return;
        }
        try {
            String payloadType = payload.getClass().getName();
            String payloadJson = objectMapper.writeValueAsString(payload);
            FanoutPushCommand cmd = new FanoutPushCommand()
                    .setUserId( userIds)
                    .setEventType(eventType.getAction())
                    .setPayloadType(payloadType)
                    .setPayloadJson(payloadJson);
            // fanout 交换机忽略 routing key，传空串即可
            this.rabbitTemplate.convertAndSend(PushMqConst.LINK_EVENT_EXCHANGE, PushMqConst.DIRECT_EVENT_PUSH_KEY, cmd);
        } catch (Exception e) {
            // 推送是「尽力而为」的在线态：发布失败不应阻断主业务，记日志即可
            log.error("批量发布推送指令失败 userIds={}, eventType={}", userIds, eventType, e);
        }
    }
}
