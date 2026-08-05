package com.link.restapi.module.redpack.model.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月02日
 */
@Data
@Accessors(chain = true)
public class LinkGrabRedPacketVO {
    private int grabStatus;

    private BigDecimal amount;

    private boolean best;

    private LinkRedPacketVO packet;
}
