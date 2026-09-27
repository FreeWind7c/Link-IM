package com.link.restapi.module.redpack.model.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RedPacketRemainInfoVO {
    private int remainCount;
    private BigDecimal remainAmount;
}