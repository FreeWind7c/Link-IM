package com.link.restapi.wallet.controller;

import com.link.im.util.ApiResult;
import com.link.restapi.wallet.model.dto.LinkWalletTopUpDto;
import com.link.restapi.wallet.model.dto.LinkWalletWithdrawDto;
import com.link.restapi.wallet.service.WalletInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.swing.plaf.PanelUI;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月07日
 */
@RestController
@RequestMapping("/wallet-info")
public class WalletInfoController {


    @Autowired
    private WalletInfoService walletInfoService;


    @PostMapping("/withdraw")
    public ApiResult withdraw(@RequestBody LinkWalletWithdrawDto dto){
        return walletInfoService.withdraw(dto);
    }

    @PostMapping("/top-up")
    public ApiResult topUp(@RequestBody LinkWalletTopUpDto dto){
        return walletInfoService.topUp(dto);
    }
}
