package com.link.restapi.module.ai.model.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月26日
 */
@Data
@Accessors(chain = true)
public class LinkAIQueryUserDTO {
    private List<String> userId;

}
