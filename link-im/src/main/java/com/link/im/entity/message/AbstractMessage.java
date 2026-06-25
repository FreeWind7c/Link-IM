package com.link.im.entity.message;

import com.link.im.entity.base.BaseData;
import lombok.Data;
import lombok.ToString;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月12日
 */
@Data
@ToString
@CompoundIndexes({
        @CompoundIndex(name = "idx_chat_seq", def = "{'chat_id':1,'seq':1}"),
        @CompoundIndex(name = "idx_chat_time", def = "{'chat_id':1,'timestamp':1}"),
        @CompoundIndex(name = "idx_chat_type_time", def = "{'chat_id':1,'type':1,'timestamp':1}"),
})
public abstract class AbstractMessage {

    @Id
    private String id;

    private int seq;

    private int type;

    @Field("chat_id")
    @Indexed
    private String chatId;

    @Field("snd_id")
    private String sndId;

    @Field("rcv_id")
    private String rcvId;

    private int state;

    private BaseData data;

    private long timestamp;

}
