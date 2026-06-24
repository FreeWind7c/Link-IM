package com.link.im.serialize;

import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.link.im.entity.base.BaseData;
import com.link.im.entity.message.AbstractMessage;
import com.link.im.entity.message.type.MessageType;

import java.lang.reflect.Type;

/**
 * AbstractMessage 的多态反序列化器。
 *
 * <p>问题背景：AbstractMessage.data 的声明类型是 BaseData（父类），Gson 默认按声明类型
 * 还原，会把它反序列化成空的 BaseData，导致 TextData.content 等子类字段全部丢失。
 *
 * <p>解决方式：先读消息体里的 type 字段，借助 {@link MessageType} 反查出 data 对应的
 * 具体子类，再把 data 子对象单独反序列化成该子类后回填。其余标量字段交给上下文按目标
 * 类型正常反序列化。
 *
 * <p>通过 registerTypeHierarchyAdapter 注册，对 AbstractMessage 的所有子类（如
 * DefaultMessageInfo）生效。
 */
public class AbstractMessageDeserializer implements JsonDeserializer<AbstractMessage> {

    /** 不带本适配器的纯 Gson，用于还原除 data 外的标量字段，避免递归调用自身。 */
    private static final Gson PLAIN = new Gson();

    @Override
    public AbstractMessage deserialize(JsonElement json, Type typeOfT,
                                       JsonDeserializationContext context) throws JsonParseException {

        JsonObject obj = json.getAsJsonObject();

        // 先按 type 取出 data 子对象，并把它从 JSON 里摘掉。
        // 因为 BaseData 是抽象类，若让 PLAIN 直接还原整个对象，它会试图 new BaseData() 而抛异常。
        JsonElement dataElement = obj.remove("data");

        // 此刻 obj 已不含 data，PLAIN 能安全还原 DefaultMessageInfo 的其余标量字段。
        AbstractMessage message = PLAIN.fromJson(obj, typeOfT);

        // 再把 data 单独反序列化成具体子类后回填。
        if (dataElement != null && !dataElement.isJsonNull() && obj.has("type")) {
            int type = obj.get("type").getAsInt();
            MessageType messageType = MessageType.fromType(type);
            if (messageType != null) {
                Class<? extends BaseData> dataClass = messageType.getDataClass();
                BaseData data = PLAIN.fromJson(dataElement, dataClass);
                message.setData(data);
            }
        }

        return message;
    }
}
