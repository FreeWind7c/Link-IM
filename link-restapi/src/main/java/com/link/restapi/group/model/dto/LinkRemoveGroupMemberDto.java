package com.link.restapi.group.model.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月27日
 */
@Data
@Accessors(chain = true)
public class LinkRemoveGroupMemberDto {

    private String groupId;

    private List<String> removedUserId;

    private String operationUserId;


}
