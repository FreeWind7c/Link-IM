package com.link.im.entity.base;

import com.alibaba.fastjson.JSONObject;
import com.google.gson.Gson;
import com.link.im.entity.message.quote.QuoteRef;
import com.link.im.entity.message.type.MessageType;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月12日
 */
@Data
@ToString
@Accessors(chain = true)
public class BaseMessage {
    @Id
    private ObjectId id;

    private int seq;

    private int type;

    @Field("chat_id")
    @Indexed
    private String chatId;

    @Field("snd_id")
    private ObjectId sndId;

    @Field("rcv_id")
    private ObjectId rcvId;

    private int state;

    /**
     * 消息数据的 JSON 字符串，入库字段。
     * 便于 MongoDB 查看和查询。
     */
    private String data;

    /**
     * 消息数据的解析后对象，不入库，只用于业务逻辑处理。
     * 由反序列化器在接收消息时自动填充。
     */
    @Transient
    private BaseData baseData;

    private long timestamp;

    /**
     * 引用的消息快照。为 null 表示非引用消息（老数据天然兼容）。
     * 客户端只传 {@code {msgId, seq, chatId}} 定位字段，其余快照由服务端回查补全。
     */
    private QuoteRef quote;

    /**
     * 获取解析后的 data 对象。优先返回缓存的 baseData，
     * 如果为 null 则从 data 字符串解析。
     */
    public BaseData getBaseData() {
        if (baseData != null) {
            return baseData;
        }
        if (data != null && type > 0) {
            MessageType messageType = MessageType.fromType(type);
            if (messageType != null) {
                baseData = new Gson().fromJson(data, messageType.getDataClass());
            }
        }
        return baseData;
    }


    /**
     * 设置 data 对象，同时更新 data 字符串和 baseData 缓存。
     */
}
