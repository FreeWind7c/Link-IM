package com.link.im.config;

import com.google.gson.Gson;
import com.link.im.entity.base.BaseData;
import com.link.im.entity.message.type.MessageType;
import org.bson.Document;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.util.Arrays;

/**
 * MongoDB 自定义转换器配置。
 * 让 BaseData 在存储时自动转成 JSON String，读取时自动解析回对象。
 */
@Configuration
public class MongoConverterConfig {

    private static final Gson GSON = new Gson();

    /**
     * BaseData → String (写入 MongoDB 时)
     */
    public static class BaseDataWriteConverter implements Converter<BaseData, String> {
        @Override
        public String convert(BaseData source) {
            return GSON.toJson(source);
        }
    }

    /**
     * String → BaseData (从 MongoDB 读取时)
     * 注意：这个转换器可能不会被调用，因为 MongoDB 读取时是 String，
     * 需要配合 @Field 和自定义读取逻辑。
     */
    public static class BaseDataReadConverter implements Converter<String, BaseData> {
        @Override
        public BaseData convert(String source) {
            // 这里无法知道具体类型，返回 null，让 BaseMessageDeserializer 处理
            return null;
        }
    }

    @Bean
    public MongoCustomConversions customConversions() {
        return new MongoCustomConversions(Arrays.asList(
                new BaseDataWriteConverter()
        ));
    }
}
