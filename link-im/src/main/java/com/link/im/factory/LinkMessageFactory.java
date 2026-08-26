package com.link.im.factory;

import com.link.core.util.seq.MessageSeqAllocator;
import com.link.im.entity.base.BaseData;
import com.link.im.entity.base.BaseMessage;
import com.link.im.entity.message.DefaultMessageInfo;
import com.link.im.entity.message.GroupMessageInfo;
import com.link.im.entity.message.type.MessageType;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.IllegalFormatCodePointException;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月24日
 */
@Slf4j
@Component
public class LinkMessageFactory{

    @Autowired
    private MessageSeqAllocator seqAllocator;

    @Autowired
    private RedisTemplate redisTemplate;


    public <T extends BaseMessage> BaseMessage create(String chatId, ObjectId sndId, ObjectId rcvId, int type, BaseData data, Class<T> clazz){
        if (clazz ==  DefaultMessageInfo.class)
            return createDefaultMessage(chatId,sndId,rcvId,type,data);
        else
           return createGroupMessage(chatId,sndId,rcvId,type,data);
    }

    private GroupMessageInfo createGroupMessage(String chatId, ObjectId sndId, ObjectId rcvId, int type, BaseData data) {
        GroupMessageInfo message = new GroupMessageInfo();
        ObjectId messageId = new ObjectId();

        MessageSeqAllocator.SeqResult result = seqAllocator.allocate(chatId, messageId.toHexString());

        int seq = result.duplicate()
                ? (int) this.seqAllocator.getMessageSeq(
                message.getChatId(),
                message.getId().toHexString())
                : (int) result.seq();

        message.setId(messageId)
                .setType(type)
                .setSeq(seq)
                .setChatId(chatId)
                .setSndId(sndId)
                .setRcvId(rcvId)
                .setState(1)
                .setData(data.toJson())
                .setBaseData(data)
                .setTimestamp(System.currentTimeMillis());
        return message;
    }

    private DefaultMessageInfo createDefaultMessage(String chatId, ObjectId sndId, ObjectId rcvId, int type, BaseData data) {
        DefaultMessageInfo message = new DefaultMessageInfo();
        ObjectId messageId = new ObjectId();

        MessageSeqAllocator.SeqResult result = seqAllocator.allocate(chatId, messageId.toHexString());

        int seq = result.duplicate()
                ? (int) this.seqAllocator.getMessageSeq(
                message.getChatId(),
                message.getId().toHexString())
                : (int) result.seq();

        message.setId(messageId)
                .setType(type)
                .setSeq(seq)
                .setChatId(chatId)
                .setSndId(sndId)
                .setRcvId(rcvId)
                .setState(1)
                .setData(data.toJson())
                .setBaseData(data)
                .setTimestamp(System.currentTimeMillis());
        return message;
    }

}
