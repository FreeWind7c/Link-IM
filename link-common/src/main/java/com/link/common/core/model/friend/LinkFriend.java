package com.link.common.core.model.friend;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月21日
 *
 */
@Data
@Accessors(chain = true)
public class LinkFriend {

    private String requestId;

    private String userId;

    private String nickname;

    private String avatar;
}
