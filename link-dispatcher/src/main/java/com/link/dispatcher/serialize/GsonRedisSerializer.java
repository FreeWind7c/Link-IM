package com.link.dispatcher.serialize;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.link.base.entity.base.BaseBotData;
import org.bson.types.ObjectId;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 通用 Redis 值序列化器：任意对象 -> JSON，读回来还原成原类型。
 *
 * <p>替代原先的 {@link org.springframework.data.redis.serializer.StringRedisSerializer}——那个只吃
 * String，写 Boolean / 实体对象会在 {@code AbstractOperations.rawValue} 阶段抛 ClassCastException。
 *
 * <h3>存储格式</h3>
 * <ul>
 *   <li><b>String 原样存</b>：不加引号、不包壳。token、会话成员集合、幂等标记这些本来就是字符串的 key
 *       在 redis-cli 里保持可读，也和历史数据、{@code StringRedisTemplate}、Lua 脚本完全兼容。</li>
 *   <li><b>其他类型包一层类型信息</b>：{@code {"@class":"全限定类名","@value":{...}}}。
 *       读的时候先看 @class 才知道该还原成什么——Redis 里只有字节，不带类型，
 *       不记类名就没法把 JSON 变回 RedPacketItem。</li>
 * </ul>
 *
 * <p>注意 @class 里写死了包名类名：实体改包或改名后，Redis 里的老数据就还原不了（会抛
 * SerializationException），要么清 key 要么做兼容。另外它会按 Redis 里的类名反射建对象，
 * 所以 Redis 必须是内网可信的，别把它暴露到公网。
 *
 * <p>ObjectId 单独挂了适配器：Gson 默认按字段反射，会把它拆成 timestamp/counter 等内部字段，
 * 还原时又拼不回来，这里统一按 24 位 hex 字符串存取。
 *
 * @Author: 无敌代码写手
 */
public class GsonRedisSerializer implements RedisSerializer<Object> {

    private static final String TYPE_FIELD = "@class";

    private static final String VALUE_FIELD = "@value";

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(ObjectId.class, new ObjectIdTypeAdapter().nullSafe())
            // AIBotMessageDTO.data 声明成父类 BaseBotData，不挂适配器读回来就是个空壳，
            // question / answer 全丢。与网络侧 LinkImSerializeConfig 用同一套判定。
            .registerTypeHierarchyAdapter(BaseBotData.class, new BaseBotDataDeserializer())
            .create();

    @Override
    public byte[] serialize(Object value) throws SerializationException {
        if (value == null)
            return new byte[0];

        // 字符串直接落原文，保持 redis-cli 可读 + 兼容历史数据
        if (value instanceof String str)
            return str.getBytes(StandardCharsets.UTF_8);

        JsonObject wrapper = new JsonObject();
        wrapper.addProperty(TYPE_FIELD, value.getClass().getName());
        wrapper.add(VALUE_FIELD, GSON.toJsonTree(value));
        return GSON.toJson(wrapper).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Object deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length == 0)
            return null;

        String json = new String(bytes, StandardCharsets.UTF_8);
        JsonObject wrapper = asTypedWrapper(json);
        // 不是带类型信息的壳子，说明当初存的就是纯字符串
        if (wrapper == null)
            return json;

        String className = wrapper.get(TYPE_FIELD).getAsString();
        try {
            Class<?> type = ClassUtils.forName(className, ClassUtils.getDefaultClassLoader());
            return GSON.fromJson(wrapper.get(VALUE_FIELD), type);
        } catch (ClassNotFoundException | LinkageError e) {
            throw new SerializationException("反序列化失败，找不到类型 " + className, e);
        }
    }

    /**
     * 判断这段内容是不是本序列化器写出的类型壳子。
     *
     * @return 是则返回解析后的壳子，不是（含解析失败）返回 null，交给调用方按纯字符串处理
     */
    private static JsonObject asTypedWrapper(String json) {
        if (json.isEmpty() || json.charAt(0) != '{')
            return null;
        try {
            JsonElement element = JsonParser.parseString(json);
            if (!element.isJsonObject())
                return null;
            JsonObject obj = element.getAsJsonObject();
            return obj.has(TYPE_FIELD) && obj.has(VALUE_FIELD) ? obj : null;
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    private static final class ObjectIdTypeAdapter extends TypeAdapter<ObjectId> {

        @Override
        public void write(JsonWriter out, ObjectId value) throws IOException {
            out.value(value.toHexString());
        }

        @Override
        public ObjectId read(JsonReader in) throws IOException {
            return new ObjectId(in.nextString());
        }
    }
}
