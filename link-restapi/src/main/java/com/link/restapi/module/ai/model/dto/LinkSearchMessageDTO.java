package com.link.restapi.module.ai.model.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月27日
 */

@Data
@Accessors(chain = true)
public class LinkSearchMessageDTO {

    private String userId;

    private String chatId;

    private int limit;

}
