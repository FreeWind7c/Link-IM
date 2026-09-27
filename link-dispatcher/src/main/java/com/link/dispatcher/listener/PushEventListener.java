package com.link.dispatcher.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.link.common.constants.publisher.PublisherRouterKeys;
import com.link.common.core.event.EventType;
import com.link.common.core.mq.FanoutPushCommand;
import com.link.dispatcher.sender.LinkMessageSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 推送指令监听器
 *
 * <p>运行在持有 Netty 长连接的进程内，因此 LinkMessageSender 拿到的
 * sessionManager.sessionMap 是「本节点真实在线表」——这正是 restapi 进程做不到、必须跨进程的原因。
 *
 * <p>拓扑直接声明在 @RabbitListener 上：由监听容器统一「建队列→绑交换机→消费」，
 * 避免「先建匿名队列 bean、再用 SpEL 引用其随机名」时的时序错位（会报 404 no queue ..._awaiting_declaration）。
 * {@code @Queue} 不写 value=匿名队列，默认 exclusive+autoDelete，节点专属、下线即清理。
 *
 * <p>广播语义下本节点可能收到「目标用户不在本节点」的指令：LinkMessageSender 内部查不到 session
 * 会直接 no-op 返回，符合预期，不算错误。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月25日
 */
@Slf4j
@Component
public class PushEventListener {


    @Autowired
    private LinkMessageSender sender;

    @Autowired
    private ObjectMapper objectMapper;


    @RabbitListener(queues = PublisherRouterKeys.LINK_EVENT_PUSH_QUEUE)
    public void onPushCommand(FanoutPushCommand cmd) {
        if (cmd == null || cmd.getUserId() == null || cmd.getUserId().isEmpty()) {
            log.warn("收到非法 FanoutPushCommand，丢弃：{}", cmd);
            return;
        }

        EventType eventType = EventType.fromAction(cmd.getEventType());
        if (eventType == null) {
            log.warn("未知 eventType action={}，丢弃推送指令 userId={}", cmd.getEventType(), cmd.getUserId());
            return;
        }

        Object payload;
        try {
            // 按发布端写入的全限定类名还原具体 payload 类型（LinkFriend / LinkApproveFriend …）
            Class<?> payloadClass = Class.forName(cmd.getPayloadType());

            payload = objectMapper.readValue(cmd.getPayloadJson(), payloadClass);

        } catch (Exception e) {
            // payload 还原失败属脏数据，记日志丢弃即可——重试也不会变好，不要重回队列打转
            log.error("还原 FanoutPushCommand payload 失败，丢弃。type={}, userId={}",
                    cmd.getPayloadType(), cmd.getUserId(), e);
            return;
        }
        // 本节点持有该用户连接则真正下发；否则 sender 内部 no-op（广播下的正常情况）
        // 遍历所有用户ID进行推送
        for (String userId : cmd.getUserId()) {
            this.sender.send(userId, eventType, payload);
        }
    }
}
