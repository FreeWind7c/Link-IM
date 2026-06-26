package com.link.im.entity.group;

import com.link.im.entity.base.BaseEntity;
import lombok.Data;
import lombok.experimental.Accessors;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月21日
 */
@Data
@Accessors(chain = true)
@Document(collection = GroupInfo.COLLECTION_NAME)
@CompoundIndexes({
        @CompoundIndex(name = "idx_user_status", def = "{'user_id':1,'status':1}"),
})
public class GroupInfo extends BaseEntity {

    public static final String COLLECTION_NAME = "group_info";

    private String title;

    private String avatar;

    @Field("owner_id")
    @Indexed
    private ObjectId ownerId;

//    @Field("chat_id")
//    @Indexed
//    public String chatId;

    // 简介
    private String introduction;

    // 通知
    private String notice;

    @Field("max_num")
    private int maxNum;

    @Field("group_member_size")
    private int groupMemberSize;

    @Field("admin_user_ids")
    private List<String> adminUserIds;

    public GroupInfo create(String ownerId, String ownerName, List<ObjectId> memberIds) {
        return this.setTitle(ownerName+"的群聊")
                .setOwnerId(new ObjectId(ownerId))
                .setMaxNum(500)
                .setGroupMemberSize(memberIds.size());
    }
}
