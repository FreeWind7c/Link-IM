package com.link.dispatcher.config.rabbit;

import com.link.common.constants.publisher.PublisherRouterKeys;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;



/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年09月03日
 */
@Configuration
public class RabbitQueueConfig {

    /**
     * 分区队列 0 - 配置死信交换机
     * 重试3次失败后，消息会自动进入死信队列
     */
    @Bean
    public Queue messageHandlerQueue0(){
        return QueueBuilder.durable(PublisherRouterKeys.MESSAGE_HANDLER_QUEUE+".0")
                .deadLetterExchange(PublisherRouterKeys.LINK_DEAD_EXCHANGE)
                .deadLetterRoutingKey(PublisherRouterKeys.LINK_DEAD_MESSAGE_KEY)
                .build();
    }

    /**
     * 分区队列 1 - 配置死信交换机
     */
    @Bean
    public Queue messageHandlerQueue1(){
        return QueueBuilder.durable(PublisherRouterKeys.MESSAGE_HANDLER_QUEUE+".1")
                .deadLetterExchange(PublisherRouterKeys.LINK_DEAD_EXCHANGE)
                .deadLetterRoutingKey(PublisherRouterKeys.LINK_DEAD_MESSAGE_KEY)
                .build();
    }

    /**
     * 分区队列 2 - 配置死信交换机
     */
    @Bean
    public Queue messageHandlerQueue2(){
        return QueueBuilder.durable(PublisherRouterKeys.MESSAGE_HANDLER_QUEUE+".2")
                .deadLetterExchange(PublisherRouterKeys.LINK_DEAD_EXCHANGE)
                .deadLetterRoutingKey(PublisherRouterKeys.LINK_DEAD_MESSAGE_KEY)
                .build();
    }

    /**
     * 分区队列 3 - 配置死信交换机
     */
    @Bean
    public Queue messageHandlerQueue3(){
        return QueueBuilder.durable(PublisherRouterKeys.MESSAGE_HANDLER_QUEUE+".3")
                .deadLetterExchange(PublisherRouterKeys.LINK_DEAD_EXCHANGE)
                .deadLetterRoutingKey(PublisherRouterKeys.LINK_DEAD_MESSAGE_KEY)
                .build();
    }



    @Bean
    public Queue messageRetryQueue() {
        return QueueBuilder
                .durable(PublisherRouterKeys.MESSAGE_RETRY_QUEUE)
                .ttl(5000)
                .build();
    }

    @Bean
    public Queue defaultmessageStorageQueue(){
        return QueueBuilder.durable(PublisherRouterKeys.DEFAULT_MESSAGE_STORAGE_QUEUE)
                .build();
    }


    @Bean
    public Queue linkDeadMessageQueue() {
        return QueueBuilder
                .durable(PublisherRouterKeys.LINK_DEAD_MESSAGE_QUEUE)
                .build();
    }


    @Bean
    public Queue linkDeadSessionQueue() {
        return QueueBuilder
                .durable(PublisherRouterKeys.LINK_DEAD_SESSION_QUEUE)
                .build();
    }


    @Bean
    public Queue groupmessageStorageQueue(){
        return QueueBuilder.durable(PublisherRouterKeys.GROUP_MESSAGE_STORAGE_QUEUE)
                .build();
    }

    @Bean
    public Queue directEventPushQueue() {
        return QueueBuilder
                .durable(PublisherRouterKeys.LINK_EVENT_PUSH_QUEUE)
                .build();
    }
}
