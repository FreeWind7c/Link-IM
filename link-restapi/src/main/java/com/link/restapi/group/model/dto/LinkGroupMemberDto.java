package com.link.restapi.group.model.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月26日
 */
@Data
@Accessors(chain = true)
public class LinkGroupMemberDto {

    private String userId;

    private String nickname;

}
