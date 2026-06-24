package com.link.restapi.chat.model.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月22日
 */
@Data
@Accessors(chain = true)
public class LinkPullChatDTO {

    private String userId;

    private int skip;

    private int limit;

}
