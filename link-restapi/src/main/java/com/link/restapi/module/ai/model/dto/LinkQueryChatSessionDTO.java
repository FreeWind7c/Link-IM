package com.link.restapi.module.ai.model.dto;

import com.link.restapi.utils.ApiResult;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月27日
 */
@Data
@Accessors(chain = true)
public class LinkQueryChatSessionDTO {

    private String chatId;

}
