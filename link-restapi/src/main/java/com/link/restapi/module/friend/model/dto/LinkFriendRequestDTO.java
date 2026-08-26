package com.link.restapi.module.friend.model.dto;

import com.link.common.pager.PageRequest;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月25日
 */
@Data
@Accessors(chain = true)
public class LinkFriendRequestDTO {
    private String userId;

    private PageRequest page;
}
