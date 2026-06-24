package com.link.core.model.friend;

import com.link.im.entity.user.UserInfo;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.redis.core.index.PathBasedRedisIndexDefinition;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月21日
 */
@Data
@Accessors(chain = true)
public class LinkFriend {

    private String userId;

    private String nickname;

    private String avatar;

    public  LinkFriend create(UserInfo user) {
        return this.setUserId(user.getId().toHexString()).setNickname(user.getNickname()).setAvatar(user.getAvatar());
    }
}
