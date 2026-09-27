package com.link.dispatcher.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.link.common.serialize.JacksonObjectIdModule;
import org.bson.types.ObjectId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;


/**
 * Jackson 配置，注册 ObjectId 的序列化器/反序列化器。
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // 忽略未知字段，避免 MQ 消息格式不匹配时报错
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        SimpleModule module = new SimpleModule();
        module.addSerializer(ObjectId.class, new JacksonObjectIdModule.ObjectIdSerializer());
        module.addDeserializer(ObjectId.class, new JacksonObjectIdModule.ObjectIdDeserializer());
        mapper.registerModule(module);
        return mapper;
    }
}
