package com.link.im.entity.rtc;

import lombok.Data;
import lombok.experimental.Accessors;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月16日
 */
@Data
@Accessors(chain = true)
public class TrtcCallParticipant {

    private ObjectId userId;

    private int status;

    @Field("join_time")
    private long joinTime;

    @Field("leave_time")
    private long leaveTime;

    private boolean initiator;


}
