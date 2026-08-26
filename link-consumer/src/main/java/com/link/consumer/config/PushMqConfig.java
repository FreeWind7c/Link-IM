package com.link.consumer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.link.common.constants.publisher.PublisherRouterKeys;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 推送消费端配置。仅在持有 Netty 长连接的进程（link-server-starter）中随 link-consumer 被加载。
 *
 * <p>fanout 广播的拓扑（交换机 + 本节点匿名队列 + 绑定）直接声明在 {@code PushCommandListener}
 * 的 @RabbitListener 上，由监听容器统一创建——避免在此手动建匿名队列 bean 再用 SpEL 引用其随机名
 * 时的时序错位。本类只留进程级的基础设施 bean。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月25日
 */
@Configuration
public class PushMqConfig {


    /**
     * Exchange
     */
    @Bean
    public DirectExchange messageExchange() {
        return new DirectExchange(
                PublisherRouterKeys.MESSAGE_EXCHANGE
        );
    }

    /**
     * 普通消息队列
     */
    @Bean
    public Queue defaultMessageStorageQueue() {
        return new Queue(
                PublisherRouterKeys.DEFAULT_MESSAGE_STORAGE_QUEUE
        );
    }

    /**
     * 普通消息 Binding
     */
    @Bean
    public Binding defaultMessageStorageBinding(
            DirectExchange messageExchange,
            Queue defaultMessageStorageQueue) {

        return BindingBuilder
                .bind(defaultMessageStorageQueue)
                .to(messageExchange)
                .with(PublisherRouterKeys.DEFAULT_MESSAGE_STORAGE_ROUTING_KEY);
    }

    /**
     * 群消息队列
     */
    @Bean
    public Queue groupMessageStorageQueue() {
        return new Queue(
                PublisherRouterKeys.GROUP_MESSAGE_STORAGE_QUEUE
        );
    }

    /**
     * 群消息 Binding
     */
    @Bean
    public Binding groupMessageStorageBinding(
            DirectExchange messageExchange,
            Queue groupMessageStorageQueue) {

        return BindingBuilder
                .bind(groupMessageStorageQueue)
                .to(messageExchange)
                .with(PublisherRouterKeys.GROUP_MESSAGE_STORAGE_ROUTING_KEY);
    }

    /**
     * 消息转换器：让 DirectPushCommand 以 JSON 收发（与发布端一致）。
     * 声明为 Bean 后，Spring Boot 自动配置的监听容器工厂会采用它。
     */
    @Bean
    public MessageConverter pushMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Netty 进程（server-starter）classpath 无 webmvc，不会自动装配 ObjectMapper，
     * 而 PushCommandListener 需要它还原 payload。此处兜底提供一个；
     * 若所在进程已有（如未来引入 web），@ConditionalOnMissingBean 让位给那个，避免冲突。
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper pushObjectMapper() {
        return new ObjectMapper();
    }
}
