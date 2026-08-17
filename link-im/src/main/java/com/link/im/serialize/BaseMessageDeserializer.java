package com.link.im.serialize;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.link.common.serialize.ObjectIdTypeAdapter;
import com.link.im.entity.base.BaseData;
import com.link.im.entity.base.BaseMessage;
import com.link.im.entity.message.type.MessageType;
import org.bson.types.ObjectId;

import java.lang.reflect.Type;

/**
 * BaseMessage 的多态反序列化器。
 *
 * <p>功能：
 * 1. 将前端发来的 data（JSON对象）同时填充到两个字段：
 *    - data: 序列化成 JSON String（入库字段）
 *    - baseData: 反序列化成具体的 BaseData 子类（业务逻辑字段，不入库）
 * 2. 根据 type 字段自动识别 data 的具体子类（TextData、ImageData 等）
 *
 * <p>通过 registerTypeHierarchyAdapter 注册，对 BaseMessage 的所有子类生效。
 */
public class BaseMessageDeserializer implements JsonDeserializer<BaseMessage> {

    /**
     * 带 ObjectId 适配器的 Gson，用于还原除 data 外的标量字段。
     * 必须注册 ObjectIdTypeAdapter，否则无法将 String 转换成 ObjectId。
     */
    private static final Gson PLAIN = new GsonBuilder()
            .registerTypeAdapter(ObjectId.class, new ObjectIdTypeAdapter())
            .create();

    @Override
    public BaseMessage deserialize(JsonElement json, Type typeOfT,
                                   JsonDeserializationContext context) throws JsonParseException {

        JsonObject obj = json.getAsJsonObject();

        // 先取出 data 子对象，并把它从 JSON 里摘掉
        JsonElement dataElement = obj.remove("data");

        // 此刻 obj 已不含 data，PLAIN 能安全还原其余标量字段
        BaseMessage message = PLAIN.fromJson(obj, typeOfT);

        // 同时填充 data（String）和 baseData（BaseData 子类）
        if (dataElement != null && !dataElement.isJsonNull() && obj.has("type")) {
            int type = obj.get("type").getAsInt();
            MessageType messageType = MessageType.fromType(type);
            if (messageType != null) {
                // 1. data 字段：序列化成 JSON String（入库）
                message.setData(PLAIN.toJson(dataElement));

                // 2. baseData 字段：反序列化成具体子类（业务逻辑使用）
                Class<? extends BaseData> dataClass = messageType.getDataClass();
                BaseData baseData = PLAIN.fromJson(dataElement, dataClass);
                message.setBaseData(baseData);
            }
        }

        return message;
    }
}
