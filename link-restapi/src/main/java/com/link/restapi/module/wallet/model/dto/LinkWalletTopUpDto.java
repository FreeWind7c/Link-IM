package com.link.restapi.module.wallet.model.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月07日
 */
@Data
@Accessors(chain = true)
public class LinkWalletTopUpDto {

    private String userId;

    private String SecretKey;
}
