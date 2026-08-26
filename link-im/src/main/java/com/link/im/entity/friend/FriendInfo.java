package com.link.im.entity.friend;

import com.link.common.constants.friend.LinkFriendStatus;
import com.link.im.entity.base.BaseEntity;
import lombok.Data;
import lombok.experimental.Accessors;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月21日
 */
@Data
@Accessors(chain = true)
@Document(collection = FriendInfo.COLLECTION_NAME)
@CompoundIndexes({
        @CompoundIndex(name = "idx_user_status", def = "{'user_id':1,'status':1}"),
        @CompoundIndex(name = "idx_user_friend", def = "{'user_id':1,'friend_id':1}",unique = true),
        @CompoundIndex(name = "idx_user_friend_status", def = "{'user_id':1,'friend_id':1,'status':1}")
})
public class FriendInfo extends BaseEntity {

    public static final String COLLECTION_NAME = "friend_info" ;

    @Field("user_id")
    @Indexed
    private ObjectId userId;

    @Field("friend_id")
    private ObjectId friendId;

    private String remark;

    /**
     * 状态 1=正常，2=删除，3=黑名单
     */
    private int status;
    /**
     * 来源	1.id添加	2.账号名添加	3.二维码添加	4.二维码添加	5.群聊添加
     */
    private int source;

    @Field("show_top")
    private boolean showTop;

    // 免打扰
    private boolean silence;


    public FriendInfo create(String userId,String friendId, int source) {
        return this.setUserId(new ObjectId(userId))
                .setFriendId(new ObjectId(friendId))
                .setStatus(LinkFriendStatus.F)
                .setSource(source)
                ;
    }
}
