package com.link.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月18日
 */
@Configuration
public class RedisConfig {

    /**
     * 自定义 RedisTemplate：key/value 全用 String 序列化。
     *
     * <p>默认 RedisTemplate 用 JDK 序列化，写进 Redis 是二进制乱码、不可跨语言读取。
     * 这里统一改成可读的 String。连接工厂由 Spring Boot 按 spring.data.redis.* 自动装配后注入。
     *
     * <p>注意：未用 Jackson 的 JSON 序列化器。Spring Boot 4 已切到 Jackson 3
     * (tools.jackson.*)，旧的 GenericJackson2JsonRedisSerializer 不再匹配 classpath。
     * 若后续要按对象 JSON 缓存，再换成 Jackson 3 对应的序列化器，并自行存取 JSON 字符串。
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        RedisSerializer<String> stringSerializer = new StringRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
