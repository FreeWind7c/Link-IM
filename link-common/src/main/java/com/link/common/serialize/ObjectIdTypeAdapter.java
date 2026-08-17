package com.link.common.serialize;

import com.google.gson.*;
import org.bson.types.ObjectId;

import java.lang.reflect.Type;

/**
 * ObjectId 的 TypeAdapter，兼容前端发送的 String 格式。
 *
 * 序列化：ObjectId → String
 * 反序列化：String → ObjectId
 */
public class ObjectIdTypeAdapter implements JsonSerializer<ObjectId>, JsonDeserializer<ObjectId> {

    @Override
    public JsonElement serialize(ObjectId src, Type typeOfSrc, JsonSerializationContext context) {
        // 序列化：ObjectId → String
        return src == null ? JsonNull.INSTANCE : new JsonPrimitive(src.toHexString());
    }

    @Override
    public ObjectId deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        // 反序列化：String → ObjectId
        if (json.isJsonNull()) {
            return null;
        }
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            String hex = json.getAsString();
            return ObjectId.isValid(hex) ? new ObjectId(hex) : null;
        }
        return null;
    }
}
