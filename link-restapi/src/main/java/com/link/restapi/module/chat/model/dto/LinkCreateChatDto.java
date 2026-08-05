package com.link.restapi.module.chat.model.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月23日
 */
@Data
@Accessors(chain = true)
public class LinkCreateChatDto {
    private String userId;

    private String targetId;
}
