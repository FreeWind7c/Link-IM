package com.link.restapi.module.chat.model.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月24日
 */
@Data
@Accessors(chain = true)
public class LinkQueryChatDTO {

    private String userId;

    private String chatId;

}
