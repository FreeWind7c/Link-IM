package com.link.restapi.module.group.model.dto;

import com.link.im.util.ApiResult;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月27日
 */
@Data
@Accessors(chain = true)
public class LinkJoinGroupDto {

    private String groupId;

    private List<String> userIds;

    private String inviterUserId;

    private int source;

}
