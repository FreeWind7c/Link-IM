package com.link.common.core.model.notice;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月30日
 */
@Data
@Accessors(chain = true)
@AllArgsConstructor
public class LinkNotice {

    private String chatId;

    private String text;
}
