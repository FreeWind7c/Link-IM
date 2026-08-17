package com.link.im.entity.rtc;

import com.link.im.entity.base.BaseEntity;
import lombok.Data;
import lombok.experimental.Accessors;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月16日
 */
@Data
@Accessors(chain = true)
@Document(collection = TrtcCallInfo.COLLECTION_NAME)
public class TrtcCallInfo extends BaseEntity {

    public static final String COLLECTION_NAME = "trtc_call_info";

    @Field("initiator_id")
    private ObjectId initiatorId;

    @Field("receiver_id")
    private ObjectId receiverId;

    @Field("room_id")
    private String roomId;

    @Field("chat_id")
    private String chatId;

    @Field("start_time")
    private long startTime;

    @Field("end_time")
    private long endTime;

    private int status;

    // false一对一通话 true多人通话
    @Field("group_call")
    private boolean groupCall;

    @Field("participant_ids")
    private List<ObjectId> participantIds;


}
