package com.link.common.core.model.ack;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月18日
 */
@Data
@ToString
@AllArgsConstructor
public class LinkAck {

    private String id;

    private String chatId;

    private int seq;

}
