package com.link.consumer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.link.im.constants.publisher.PublisherRouterKeys;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
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
     * 创建 Exchange
     */
    @Bean
    public DirectExchange messageExchange() {
        return new DirectExchange(PublisherRouterKeys.MESSAGE_EXCHANGE);
    }




    @Bean
    public Queue messageStorageQueue() {
        return new Queue(PublisherRouterKeys.DEFAULT_MESSAGE_STORAGE_QUEUE);
    }


    @Bean
    public Binding messageStorageBinding(
            DirectExchange messageExchange,
            Queue messageStorageQueue) {

        return BindingBuilder
                .bind(messageStorageQueue)
                .to(messageExchange)
                .with(PublisherRouterKeys.DEFAULT_MESSAGE_STORAGE_ROUTING_KEY);
    }

    @Bean
    public Queue messageStorageQueue1() {
        return new Queue(PublisherRouterKeys.GROUP_MESSAGE_STORAGE_QUEUE);
    }


    @Bean
    public Binding messageStorageBinding1(
            DirectExchange messageExchange,
            Queue messageStorageQueue) {

        return BindingBuilder
                .bind(messageStorageQueue)
                .to(messageExchange)
                .with(PublisherRouterKeys.GROUP_MESSAGE_STORAGE_ROUTING_KEY);
    }

    /** 拓扑声明器：把监听器上 @QueueBinding 声明的 exchange/queue/binding 自动在 broker 创建 */
    @Bean
    public RabbitAdmin pushRabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
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
