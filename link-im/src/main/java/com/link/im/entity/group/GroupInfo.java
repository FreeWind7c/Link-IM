package com.link.im.entity.group;

import com.link.im.entity.base.BaseEntity;
import lombok.Data;
import lombok.experimental.Accessors;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
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
public class GroupInfo extends BaseEntity {

    public static final String COLLECTION_NAME = "group_info";

    private String title;

    private String avatar;

    @Field("owner_id")
    private ObjectId  ownerId;

    private String introduction;

    private String notice;

    @Field("max_num")
    private int maxNum;

    @Field("group_member_size")
    private int groupMemberSize;

    @Field("admin_user_ids")
    private List<String> adminUserIds;

}
