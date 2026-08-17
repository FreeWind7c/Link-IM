package com.link.im.entity.message;

import com.google.gson.Gson;
import com.link.im.dto.ai.AIBotMessageDTO;
import lombok.Data;
import lombok.experimental.Accessors;
import org.bson.types.ObjectId;
import org.springframework.beans.BeanUtils;

@Data
@Accessors(chain = true)
public class AIBotMessage{


    private ObjectId id;

    private int seq;

    private int type;

    private String chatId;

    private ObjectId sndId;

    private ObjectId rcvId;

    private String data;

    private long timestamp;

    public AIBotMessage converterMessage(AIBotMessageDTO dto) {
        AIBotMessage message = new AIBotMessage();
        BeanUtils.copyProperties(dto,message);
        message.setData(new Gson().toJson(message.getData()));
        return message;
    }
}