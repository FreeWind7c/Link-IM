package com.link.base.entity.message;

import com.google.gson.Gson;
import com.link.base.entity.base.BaseBootMessage;
import com.link.base.entity.base.BaseEntity;
import lombok.Data;
import lombok.experimental.Accessors;
import org.bson.types.ObjectId;
import org.springframework.beans.BeanUtils;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@Accessors(chain = true)
@Document(collection = AIBotMessage.COLLECTION_NAME)
public class AIBotMessage extends BaseEntity {

    public final static String COLLECTION_NAME = "bot_message_queue";

    private int seq;

    // 1用户发送 2机器人发送
    private int type;

    @Field("chat_id")
    private String chatId;

    @Field("snd_id")
    private ObjectId sndId;

    @Field("rcv_id")
    private ObjectId rcvId;

    private String data;

    private long timestamp;

    public static AIBotMessage converterMessage(BaseBootMessage dto, int type) {
        AIBotMessage message = new AIBotMessage();
        BeanUtils.copyProperties(dto,message);
        message.setType(type);
        message.setId(new ObjectId(dto.getId()));
        message.setSndId(new ObjectId(dto.getSndId()));
        message.setRcvId(new ObjectId(dto.getRcvId()));
        message.setData(new Gson().toJson(dto.getData()));
        return message;
    }
}