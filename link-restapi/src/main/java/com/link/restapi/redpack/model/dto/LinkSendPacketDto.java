package com.link.restapi.redpack.model.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月19日
 */
@Data
public class LinkSendPacketDto {

    private String sndId;

    private String chatId;

    // 业务唯一流水号
    private String bizDetailId;

    private BigDecimal amount;

    private int type;

    private int count;

    private String blessing;

}
