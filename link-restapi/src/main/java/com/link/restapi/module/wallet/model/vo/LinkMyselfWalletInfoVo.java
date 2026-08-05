package com.link.restapi.module.wallet.model.vo;

import com.link.common.pager.Pager;
import com.link.im.entity.wallet.WalletFlow;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月02日
 */
@Data
@Accessors(chain = true)
public class LinkMyselfWalletInfoVo {

    private BigDecimal balance;

    private boolean created;

    private Pager<WalletFlow> pager;

}
