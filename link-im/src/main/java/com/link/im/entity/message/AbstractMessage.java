package com.link.im.entity.message;

import com.link.im.entity.base.BaseData;
import lombok.Data;
import lombok.ToString;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月12日
 */
@Data
@ToString
public abstract class AbstractMessage {

    @Id
    private String id;

    private int seq;

    private int type;

    @Field("chat_id")
    private String chatId;

    @Field("snd_id")
    private String sndId;

    @Field("rcv_id")
    private String rcvId;

    private int state;

    private BaseData data;

    private long timestamp;

}
