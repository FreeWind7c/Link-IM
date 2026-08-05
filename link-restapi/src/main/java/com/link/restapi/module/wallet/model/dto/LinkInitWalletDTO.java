package com.link.restapi.module.wallet.model.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月02日
 */
@Data
@Accessors(chain = true)
public class LinkInitWalletDTO {

    private String userId;

    private String password;

}
