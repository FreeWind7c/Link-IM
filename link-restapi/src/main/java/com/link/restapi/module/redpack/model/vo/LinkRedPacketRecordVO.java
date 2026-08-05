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
public class LinkRedPacketRecordVO {

    private String userId;

    private String avatar;

    private String nickname;

    private BigDecimal amount;

    private boolean best;
}
