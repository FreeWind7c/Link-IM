package com.link.restapi.enums.wallet;

import com.link.restapi.enums.BaseEnum;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月02日
 */
public enum WalletApiCode implements BaseEnum {

    SECRET_KEY_NOT_EXIST(13000,"兑换失败，密钥不存在!"), WALLET_NOT_EXIST(13001 ,"用户未开通钱包");

    private int code;

    private String msg;


    WalletApiCode(int code, String msg)
    {
        this.code = code;
        this.msg = msg;
    }

    @Override
    public int getCode() {
        return this.code;
    }

    @Override
    public String getMessage() {
        return this.msg;
    }

}
