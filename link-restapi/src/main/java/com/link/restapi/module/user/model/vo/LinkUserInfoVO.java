package com.link.restapi.module.user.model.vo;

import com.link.im.entity.user.UserInfo;
import lombok.Data;
import lombok.experimental.Accessors;

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

    /** 由实体组装展示 VO。映射放在 api 层，使 link-im 实体不反向依赖 VO。 */
    public static LinkUserInfoVO fromVo(UserInfo user) {
        return new LinkUserInfoVO().setId(user.getId().toString()).setUserNo(user.getUserNo())
                .setNickname(user.getNickname()).setAvatar(user.getAvatar()).setAccount(user.getAccount())
                .setRegisterTime(user.getRegisterTime()).setLoginTime(user.getLoginTime());
    }
}
