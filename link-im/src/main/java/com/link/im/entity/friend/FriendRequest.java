package com.link.im.entity.friend;

import com.link.im.entity.base.BaseEntity;
import lombok.Data;
import lombok.experimental.Accessors;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月22日
 */
@Data
@Accessors(chain = true)
@Document(collection = FriendRequest.COLLECTION_NAME)
public class FriendRequest extends BaseEntity {

    public static final String COLLECTION_NAME = "friend_request";

    @Field("user_id")
    private ObjectId userId;

    @Field("friend_id")
    private ObjectId friendId;

    private int source;

    // 0未处理 1已同意 2已拒绝
    private int status;

}
