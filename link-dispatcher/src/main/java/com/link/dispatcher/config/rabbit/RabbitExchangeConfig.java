package com.link.dispatcher.config.rabbit;

import com.link.common.constants.publisher.PublisherRouterKeys;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年09月03日
 */
@Configuration
public class RabbitExchangeConfig {


    @Bean
    public DirectExchange messageRetryExchange() {
        return ExchangeBuilder
                .directExchange(PublisherRouterKeys.MESSAGE_RETRY_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public DirectExchange linkDeadExchange() {
        return ExchangeBuilder
                .directExchange(PublisherRouterKeys.LINK_DEAD_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public FanoutExchange linkEventExchange() {
        return ExchangeBuilder
                .fanoutExchange(PublisherRouterKeys.LINK_EVENT_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public DirectExchange messageHandlerExchange(){
        DirectExchange directExchange = new DirectExchange(
                PublisherRouterKeys.MESSAGE_HANDLER_EXCHANGE,
                true,
                false
        );
        return directExchange;
    }

    @Bean
    public DirectExchange messageStorageExchange(){
        DirectExchange directExchange = new DirectExchange(
                PublisherRouterKeys.MESSAGE_STORAGE_EXCHANGE,
                true,
                false
        );
        return directExchange;
    }

}
