package com.link.dispatcher.listener;

import com.google.gson.Gson;
import com.link.common.constants.publisher.PublisherRouterKeys;
import com.link.dispatcher.dto.MessageHandlerDTO;

import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class MessageRetryListener {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @RabbitListener(
            queues = PublisherRouterKeys.MESSAGE_RETRY_QUEUE,
            concurrency = "1",
            containerFactory = "rabbitListenerContainerFactory"
    )
    public void handleRetry(
            MessageHandlerDTO dto,
            Message message,
            Channel channel
    ) throws IOException {

        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        log.info("ttl队列收到消息:" + new Gson().toJson(message.getMessageProperties()));
        try {
            // ⚠️ 延迟5秒再重试
            Thread.sleep(5000);

            Integer partition = message.getMessageProperties().getHeader("x-partition");

            if (partition == null) {
                throw new IllegalStateException(
                        "Retry message missing partition"
                );
            }

            String routingKey = PublisherRouterKeys.MESSAGE_HANDLER_ROUTING_KEY + "." + partition;

            rabbitTemplate.convertAndSend(
                    PublisherRouterKeys.MESSAGE_HANDLER_EXCHANGE,
                    routingKey,
                    dto,
                    msg -> {
                        // 保留重试次数
                        Object retryCount =
                                message.getMessageProperties()
                                        .getHeaders()
                                        .get("x-retry-count");

                        msg.getMessageProperties()
                                .setHeader(
                                        "x-retry-count",
                                        retryCount
                                );

                        msg.getMessageProperties()
                                .setHeader(
                                        "x-partition",
                                        partition
                                );

                        msg.getMessageProperties()
                                .setDeliveryMode(
                                        MessageDeliveryMode.PERSISTENT
                                );

                        return msg;
                    }
            );

            // 重新发布成功后 ACK retry queue 中的消息
            channel.basicAck(deliveryTag, false);

            log.info(
                    "消息重试重新进入分区队列 | partition={}",
                    partition
            );

        } catch (Exception e) {

            log.error(
                    "Retry消息重新投递失败",
                    e
            );

            // 不要直接丢弃
            channel.basicNack(
                    deliveryTag,
                    false,
                    true
            );
        }
    }
}