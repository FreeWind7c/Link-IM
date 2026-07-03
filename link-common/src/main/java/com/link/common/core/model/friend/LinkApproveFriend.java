package com.link.common.core.model.friend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月22日
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LinkApproveFriend {
    private String userId;

    private String friendId;


}
