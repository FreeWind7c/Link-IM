package com.link.restapi.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.link.dispatcher.serialize.ObjectIdModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RestApiRedisConfig {


    /**
     * 声明成 Bean 后，Spring Boot 会自动把它装配进全局 ObjectMapper，
     * 于是 HTTP 响应、MQ 消息里的 ObjectId 也统一变成十六进制字符串。
     */
    @Bean
    public ObjectIdModule objectIdModule() {
        return new ObjectIdModule();
    }

    /**
     * 通用模板：key/value 都走 Jackson 序列化（带类型头），可以直接存 POJO。
     *
     * <p>注意 key 也被 JSON 化了——字符串 key 在 Redis 里是带引号的 {@code "foo"}。
     * 这一点必须和 Redisson 的裸字符串 key 区分开：同一个逻辑名在两边其实是两个不同的 key。
     * 依赖这个「巧合」来避免撞 key 是危险的，业务侧请始终用不同前缀（见 RedisKeys）。
     */
    @Bean
    //    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {

        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // 等价于已废弃的 enableDefaultTyping(NON_FINAL)，类型头仍是 WRAPPER_ARRAY，
        // 与 Redis 里现有数据的格式保持一致
        om.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL);
        // ObjectId 走字符串，避免反射成 POJO 后读不回来
        om.registerModule(new ObjectIdModule());
        // 容忍字段增减：Redis 里存的是发版前写入的旧结构，实体删字段后不能让存量数据反序列化直接炸
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer =
                new Jackson2JsonRedisSerializer<>(om, Object.class);

        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.setKeySerializer(jackson2JsonRedisSerializer);
        redisTemplate.setValueSerializer(jackson2JsonRedisSerializer);
        redisTemplate.setHashKeySerializer(jackson2JsonRedisSerializer);
        redisTemplate.setHashValueSerializer(jackson2JsonRedisSerializer);

        return redisTemplate;
    }

}
