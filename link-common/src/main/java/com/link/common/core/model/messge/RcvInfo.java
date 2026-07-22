package com.link.common.core.model.messge;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月15日
 */
@Data
@Accessors(chain = true)
public class RcvInfo {
    private String rcvId;

    private int type;

    private String chatId;
}
