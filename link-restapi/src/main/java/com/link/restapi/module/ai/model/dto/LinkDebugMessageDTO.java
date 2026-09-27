package com.link.restapi.module.ai.model.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 调试接口 - 检查消息数据
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月27日
 */
@Data
@Accessors(chain = true)
public class LinkDebugMessageDTO {

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 对方用户ID（可选）
     */
    private String friendId;
}
