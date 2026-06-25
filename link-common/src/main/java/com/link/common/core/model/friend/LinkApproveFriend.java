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
// 跨 MQ 传输需经 Jackson 反序列化：显式 @AllArgsConstructor 会顶掉隐式无参构造器，
// 必须补 @NoArgsConstructor，否则消费端报 "no Creators, like default constructor, exist"。
@NoArgsConstructor
public class LinkApproveFriend {
    private String userId;

    private String friendId;


}
