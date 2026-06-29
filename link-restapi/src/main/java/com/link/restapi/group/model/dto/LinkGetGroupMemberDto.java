package com.link.restapi.group.model.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月27日
 */
@Data
@Accessors(chain = true)
public class LinkGetGroupMemberDto {
    private String groupId;

    private int offset;

    private int limit;
}
