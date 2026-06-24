package com.link.im.entity.user;


import com.link.restapi.user.model.vo.LinkUserInfoVO;
import com.link.restapi.user.model.dto.LinkUserRegisterDTO;
import com.link.im.util.MD5Util;
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

    @Field("register_time")
    private long registerTime;

    @Field("login_time")
    private long loginTime;

    public UserInfo create(LinkUserRegisterDTO dto){
        return new UserInfo()
                .setAccount(dto.getAccount())
                .setPassword(MD5Util.encrypt(dto.getPassword()))
                .setRegisterTime(System.currentTimeMillis())
                .setNickname("用户" + dto.getAccount());
    }


    public LinkUserInfoVO createVO() {
        return new LinkUserInfoVO().setId(this.getId().toString()).setUserNo(this.getUserNo())
                .setNickname(this.getNickname()).setAvatar(this.getAvatar()).setAccount(this.getAccount())
                .setRegisterTime(this.getRegisterTime()).setLoginTime(this.getLoginTime());
    }
}
