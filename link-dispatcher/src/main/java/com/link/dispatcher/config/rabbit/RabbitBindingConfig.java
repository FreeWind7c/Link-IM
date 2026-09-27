package com.link.dispatcher.config.rabbit;

import com.link.common.constants.publisher.PublisherRouterKeys;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;



/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年09月03日
 */
@Configuration
public class RabbitBindingConfig {




    @Bean
    public Binding messageHandlerBinding0(){
        return new Binding(
                PublisherRouterKeys.MESSAGE_HANDLER_QUEUE+".0",
                Binding.DestinationType.QUEUE,
                PublisherRouterKeys.MESSAGE_HANDLER_EXCHANGE,
                PublisherRouterKeys.MESSAGE_HANDLER_ROUTING_KEY+".0",
                null);
    }

    @Bean
    public Binding messageHandlerBinding1(){
        return new Binding(
                PublisherRouterKeys.MESSAGE_HANDLER_QUEUE+".1",
                Binding.DestinationType.QUEUE,
                PublisherRouterKeys.MESSAGE_HANDLER_EXCHANGE,
                PublisherRouterKeys.MESSAGE_HANDLER_ROUTING_KEY+".1",
                null);
    }
    @Bean
    public Binding messageHandlerBinding2(){
        return new Binding(
                PublisherRouterKeys.MESSAGE_HANDLER_QUEUE+".2",
                Binding.DestinationType.QUEUE,
                PublisherRouterKeys.MESSAGE_HANDLER_EXCHANGE,
                PublisherRouterKeys.MESSAGE_HANDLER_ROUTING_KEY+".2",
                null);
    }
    @Bean
    public Binding messageHandlerBinding3(){
        return new Binding(
                PublisherRouterKeys.MESSAGE_HANDLER_QUEUE+".3",
                Binding.DestinationType.QUEUE,
                PublisherRouterKeys.MESSAGE_HANDLER_EXCHANGE,
                PublisherRouterKeys.MESSAGE_HANDLER_ROUTING_KEY+".3",
                null);
    }

    @Bean
    public Binding groupMessageStorageBinding(){
        return new Binding(
                PublisherRouterKeys.GROUP_MESSAGE_STORAGE_QUEUE,
                Binding.DestinationType.QUEUE,
                PublisherRouterKeys.MESSAGE_STORAGE_EXCHANGE,
                PublisherRouterKeys.GROUP_MESSAGE_STORAGE_ROUTING_KEY,
                null);
    }

    @Bean
    public Binding linkDeadMessageBinding() {
        return new Binding(
                PublisherRouterKeys.LINK_DEAD_MESSAGE_QUEUE,
                Binding.DestinationType.QUEUE,
                PublisherRouterKeys.LINK_DEAD_EXCHANGE,
                PublisherRouterKeys.LINK_DEAD_MESSAGE_KEY,
                null);
    }

    @Bean
    public Binding messageRetryBinding() {
        return new Binding(
                PublisherRouterKeys.MESSAGE_RETRY_QUEUE,
                Binding.DestinationType.QUEUE,
                PublisherRouterKeys.MESSAGE_RETRY_EXCHANGE,
                PublisherRouterKeys.MESSAGE_RETRY_KEY,
                null);
    }

    @Bean
    public Binding linkDeadSessionBinding() {
        return new Binding(
                PublisherRouterKeys.LINK_DEAD_SESSION_QUEUE,
                Binding.DestinationType.QUEUE,
                PublisherRouterKeys.LINK_DEAD_EXCHANGE,
                PublisherRouterKeys.LINK_DEAD_SESSION_KEY,
                null);
    }



    @Bean
    public Binding defaultMessageStorageBinding(){
        return new Binding(
                PublisherRouterKeys.DEFAULT_MESSAGE_STORAGE_QUEUE,
                Binding.DestinationType.QUEUE,
                PublisherRouterKeys.MESSAGE_STORAGE_EXCHANGE,
                PublisherRouterKeys.DEFAULT_MESSAGE_STORAGE_ROUTING_KEY,
                null);
    }

    @Bean
    public Binding linkEventPushBinding() {
        return new Binding(
                PublisherRouterKeys.LINK_EVENT_PUSH_QUEUE,
                Binding.DestinationType.QUEUE,
                PublisherRouterKeys.LINK_EVENT_EXCHANGE,
                null,
                null);
    }


}
