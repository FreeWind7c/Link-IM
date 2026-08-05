package com.link.restapi.module.config;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 发布端 MQ 配置。声明 JSON 消息转换器，Spring Boot 自动配置的 RabbitTemplate 会采用它，
 * 使 DirectPushCommand 以 JSON 发出（与 link-consumer 端的转换器对齐，保证两端能互相反序列化）。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月25日
 */
@Configuration
public class RabbitPushConfig {

    @Bean
    public MessageConverter pushMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
