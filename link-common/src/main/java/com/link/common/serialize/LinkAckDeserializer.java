package com.link.common.serialize;

import com.google.gson.*;
import com.link.common.core.model.ack.LinkAck;

import java.lang.reflect.Type;

/**
 * LinkAck 的反序列化器，兼容前端发送的对象格式 id。
 *
 * 前端可能发送两种格式：
 * 1. 字符串格式：{"id": "6a8030942a5b8a854bf0d5ce", ...}
 * 2. 对象格式：{"id": {"timestamp": 1786785940, "nonce": 3052185477583001000}, ...}
 *
 * 对于对象格式，我们将其忽略，因为 ACK 事件中的 id 通常不会被使用。
 */
public class LinkAckDeserializer implements JsonDeserializer<LinkAck> {

    private static final Gson PLAIN = new Gson();

    @Override
    public LinkAck deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {

        JsonObject obj = json.getAsJsonObject();

        // 处理 id 字段
        String id = null;
        JsonElement idElement = obj.get("id");
        if (idElement != null && !idElement.isJsonNull()) {
            if (idElement.isJsonPrimitive() && idElement.getAsJsonPrimitive().isString()) {
                // 字符串格式
                id = idElement.getAsString();
            } else if (idElement.isJsonObject()) {
                // 对象格式 - 前端的 bug，我们将其转换为 null 或者提取某个字段
                // 这里简单地设置为 null，因为 ACK 事件通常不使用这个 id
                id = null;
            }
        }

        // 提取其他字段
        String chatId = obj.has("chatId") ? obj.get("chatId").getAsString() : null;
        int seq = obj.has("seq") ? obj.get("seq").getAsInt() : 0;

        return new LinkAck(id, chatId, seq);
    }
}
