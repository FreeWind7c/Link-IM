package com.link.restapi.module.friend.model.dto;

import lombok.Data;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月21日
 */
@Data
public class LinkAddFriendDTO {
    private String userId;

    private String friendId;

    private int source;
}
