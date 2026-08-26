package com.link.im.entity.group;

import com.link.common.constants.group.GroupSourceConstant;
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
 * @CreateTime: 2026年06月26日
 */
@Data
@Accessors(chain = true)
@Document(collection = GroupMember.COLLECTION_NAME)
@CompoundIndexes({
        @CompoundIndex(name = "idx_group_user", def = "{'group_id':1,'user_id':1}",unique = true)
})
public class GroupMember extends BaseEntity {

    public static final String COLLECTION_NAME = "group_member";

    @Field("user_id")
    @Indexed
    private ObjectId userId;

    @Field("group_id")
    @Indexed
    private ObjectId groupId;


    private int role;

    private String nickname;

    // 进群方式 1初始成员 2群成员邀请 3扫码进群
    @Indexed
    private int source;

    // 邀请人id
    @Field("inviter_user_id")
    @Indexed
    private ObjectId inviterUserId;

    // 是否永久禁言
    @Field("forever_silence")
    private boolean foreverSilence;

    // 禁言截止时间
    @Field("no_speaking_until")
    private long noSpeakingUntil;

    public GroupMember create(String groupId,String userId, String inviterUserId, int role) {
        return this.setUserId(new ObjectId(userId))
                .setRole(role)
                .setGroupId(new ObjectId(groupId))
                .setSource(GroupSourceConstant.INITIAL_MEMBER)
                .setInviterUserId(new ObjectId(inviterUserId))
                .setForeverSilence(false)
                .setNoSpeakingUntil(0L);
    }

    public GroupMember create(String userId, String inviterUserId, int role) {
        return this.setUserId(new ObjectId(userId))
                .setRole(role)
                .setSource(GroupSourceConstant.INITIAL_MEMBER)
                .setInviterUserId(new ObjectId(inviterUserId))
                .setForeverSilence(false)
                .setNoSpeakingUntil(0L);
    }
}
