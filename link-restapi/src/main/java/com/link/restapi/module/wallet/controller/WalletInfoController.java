package com.link.restapi.module.wallet.controller;

import com.link.common.pager.Pager;
import com.link.im.constants.wallet.WalletTopUpStatusKeys;
import com.link.im.entity.wallet.WalletFlow;
import com.link.im.entity.wallet.WalletInfo;
import com.link.im.entity.wallet.WalletTopUp;
import com.link.im.util.ApiResult;
import com.link.im.util.MD5Util;
import com.link.restapi.module.wallet.model.dto.*;
import com.link.restapi.module.wallet.model.vo.LinkMyselfWalletInfoVo;
import com.link.restapi.module.wallet.service.WalletInfoService;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月07日
 */
@RestController
@RequestMapping("/wallet")
public class WalletInfoController {


    @Autowired
    private WalletInfoService walletInfoService;

    @Autowired
    private MongoTemplate mongoTemplate;

    @PostMapping("/created-top")
    public ApiResult createdTop(){
        String userId = "6a6a3c2404b78eb884c0f776";
        WalletTopUp walletTopUp = new WalletTopUp();
        walletTopUp.setUserId(new ObjectId(userId));
        walletTopUp.setAmount(new BigDecimal(500));
        walletTopUp.setSecretKey(MD5Util.encrypt(userId+ WalletTopUp.MD5_SECRET+System.currentTimeMillis()));
        walletTopUp.setStatus(WalletTopUpStatusKeys.UNREDEEMED);
        walletTopUp.setCreatedTime(System.currentTimeMillis());
        this.mongoTemplate.insert(walletTopUp);
        return ApiResult.success();
    }

    @PostMapping("/init-wallet")
    public ApiResult initWallet(@RequestBody LinkInitWalletDTO dto){
        WalletInfo walletInfo = new WalletInfo();
        walletInfo.setUserId(new ObjectId(dto.getUserId()))
                .setBalance(BigDecimal.ZERO)
                .setDisabled(false)
                // 支付密码只存摘要，和登录密码（LinkUserRegisterDTO）保持同一套做法。
                // 明文入库意味着任何一次库泄露 = 所有人的支付密码泄露
                .setPassword(MD5Util.encrypt(dto.getPassword()))
                .setCreatedTime(walletInfoService.now());
        this.mongoTemplate.insert(walletInfo);
        Pager<WalletFlow> pager = new Pager<WalletFlow>().setList(null).setHasMore(false).setNextCursor(null);
        LinkMyselfWalletInfoVo vo = new LinkMyselfWalletInfoVo().setPager(pager).setCreated(true).setBalance(BigDecimal.ZERO);
        return ApiResult.success().setData(vo);
    }

    @PostMapping("/myself-wallet")
    public ApiResult myselfWallet(@RequestBody LinkMyselfWalletDto dto){
        return walletInfoService.myselfWallet(dto);
    }


    /**
     * 提现
     * @param dto
     * @return
     */
    @PostMapping("/withdraw")
    public ApiResult withdraw(@RequestBody LinkWalletWithdrawDto dto){
        return walletInfoService.withdraw(dto);
    }

    /**
     * 充值
     * @param dto
     * @return
     */
    @PostMapping("/top-up")
    public ApiResult topUp(@RequestBody LinkWalletTopUpDto dto){
        return walletInfoService.topUp(dto);
    }


}
