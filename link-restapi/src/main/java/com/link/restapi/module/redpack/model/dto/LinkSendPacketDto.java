package com.link.restapi.module.redpack.model.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月19日
 */
@Data
@Accessors(chain = true)
public class LinkSendPacketDto {

    private String sndId;

    private String rcvId;

    private String chatId;

    // 业务唯一流水号
    private String bizDetailId;

    private BigDecimal amount;

    // 1单聊红包 2拼手气红包
    private int type;

    private int count;

    private String password;

    // 祝福语
    private String blessing;

}
