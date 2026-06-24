package com.link.restapi.user.model.vo;

import lombok.Data;
import lombok.experimental.Accessors;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月22日
 */
@Data
@Accessors(chain = true)
public class LinkUserInfoVO {
    private String id;

    public String userNo;

    private String nickname;

    private String avatar;

    private String account;

    private String password;
    private long registerTime;

    private long loginTime;
}
