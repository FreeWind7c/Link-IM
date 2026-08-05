package com.link.im.serialize;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;
import org.bson.types.ObjectId;

import java.io.IOException;

/**
 * ObjectId 的 Jackson 模块：统一按 24 位 hex 字符串收发。
 *
 * <p>不注册的话 Jackson 按 bean 属性反射 ObjectId，会拆成 {@code {"timestamp":...,"counter":...}}
 * 这种内部字段形态——前端拿到没法用，读回来也拼不回 ObjectId（驱动没给对应的公开构造器）。
 *
 * <p>作用范围：在 {@link com.link.im.config.RedisConfig} 里既声明成 Bean（Spring Boot 会自动
 * 装配进全局 ObjectMapper，覆盖 HTTP 响应和 MQ 消息），也手动注册进 Redis 那份独立的
 * ObjectMapper。Gson 那条链路不受影响，见 {@link GsonRedisSerializer} 里的 ObjectIdTypeAdapter。
 *
 * <p>注意 ObjectId 是 final 类，{@code activateDefaultTyping(NON_FINAL)} 不会给它包类型头，
 * 所以 Redis 里存的就是裸的 hex 字符串。
 *
 * @Author: 无敌代码写手
 */
public class ObjectIdModule extends SimpleModule {

    /** Mongo 扩展 JSON 的 ObjectId 字段名，形如 {@code {"$oid":"..."}}。 */
    private static final String EXT_JSON_FIELD = "$oid";

    public ObjectIdModule() {
        super(ObjectIdModule.class.getSimpleName());
        addSerializer(ObjectId.class, new ObjectIdSerializer());
        addDeserializer(ObjectId.class, new ObjectIdDeserializer());
        // Map<ObjectId, ?> 的 key：写出去靠 ObjectId.toString()（本身就是 hex）已经对了，
        // 但读回来 Jackson 没有内置的 key 反序列化器，得自己补
        addKeyDeserializer(ObjectId.class, new ObjectIdKeyDeserializer());
    }

    private static final class ObjectIdSerializer extends StdScalarSerializer<ObjectId> {

        ObjectIdSerializer() {
            super(ObjectId.class);
        }

        @Override
        public void serialize(ObjectId value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(value.toHexString());
        }
    }

    private static final class ObjectIdDeserializer extends StdDeserializer<ObjectId> {

        ObjectIdDeserializer() {
            super(ObjectId.class);
        }

        @Override
        public ObjectId deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            // 常规形态：hex 字符串
            if (p.hasToken(JsonToken.VALUE_STRING))
                return toObjectId(p.getText(), ctxt);

            // 容错 Mongo 扩展 JSON：{"$oid":"..."}
            if (p.hasToken(JsonToken.START_OBJECT)) {
                String hex = null;
                while (p.nextToken() != JsonToken.END_OBJECT) {
                    if (EXT_JSON_FIELD.equals(p.currentName()) && p.nextToken() == JsonToken.VALUE_STRING)
                        hex = p.getText();
                    else
                        p.skipChildren();
                }
                if (hex != null)
                    return toObjectId(hex, ctxt);
            }

            return (ObjectId) ctxt.handleUnexpectedToken(ObjectId.class, p);
        }
    }

    private static final class ObjectIdKeyDeserializer extends KeyDeserializer {

        @Override
        public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException {
            return toObjectId(key, ctxt);
        }
    }

    /**
     * hex 字符串 -> ObjectId。空串按 null 处理（表单/前端漏传字段时不至于直接 500），
     * 非法值抛 InputMismatch，别让脏数据静默变成一个新生成的 ObjectId。
     */
    private static ObjectId toObjectId(String hex, DeserializationContext ctxt) throws IOException {
        String trimmed = hex == null ? null : hex.trim();
        if (trimmed == null || trimmed.isEmpty())
            return null;
        if (!ObjectId.isValid(trimmed))
            return ctxt.reportInputMismatch(ObjectId.class, "不是合法的 ObjectId：%s", trimmed);
        return new ObjectId(trimmed);
    }
}
