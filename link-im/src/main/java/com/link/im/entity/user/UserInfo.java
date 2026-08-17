package com.link.im.entity.user;


import lombok.*;
import lombok.experimental.Accessors;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月20日
 */
@Data
@Accessors(chain = true)
@Document(collection = UserInfo.COLLECTION_NAME)
public class UserInfo {

    public static final String COLLECTION_NAME = "user_info";

    @Id
    private ObjectId id;

    @Field("user_no")
    public String userNo;

    private String nickname;

    private String avatar;

    private String account;

    private String password;

    // 0普通用户 1VIP用户 2机器人
    private int category;

    @Field("register_time")
    private long registerTime;

    @Field("login_time")
    private long loginTime;
}
