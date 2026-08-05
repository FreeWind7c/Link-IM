package com.link.restapi.module.redpack.model.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月02日
 */
@Data
@Accessors(chain = true)
public class LinkRedPacketVO {

    private String id;

    private BigDecimal totalAmount;

    private BigDecimal remainAmount;

    private int totalCount;

    private int remainCount;

    private List<LinkRedPacketRecordVO>  records;


}
