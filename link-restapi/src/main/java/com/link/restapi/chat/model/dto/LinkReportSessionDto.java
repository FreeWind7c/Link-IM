package com.link.restapi.chat.model.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月24日
 */
@Data
@Accessors(chain = true)
public class LinkReportSessionDto {

    private String userId;

    private String chatId;

    private int lastReadSeq;

}
