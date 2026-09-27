package com.link.restapi.enums.redpack;

import com.link.restapi.enums.BaseEnum;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月02日
 */
public enum RedPacketApiCode implements BaseEnum {

    WALLET_NOT_EXIST(14000,"钱包不存在,请先开通钱包功能"),
    PAYMENT_PASSWORD_ERROR(14001,"支付密码错误" ),
    WALLET_INSUFFICIENT_BALANCE(14002,"钱包余额不足" ),
    RED_PACKET_NOT_EXIST(14003,"红包不存在" ),
    RED_PACKET_EXPIRED(14004,"红包已过期" ),
    RED_PACKET_SOLD_OUT(14005,"手慢了，红包派完了" ),
    RED_PACKET_FORBIDDEN(14006,"你不在该会话中，无法领取该红包" ),
    RED_PACKET_DUPLICATE_SUBMIT(14007,"请勿重复提交" ),
    RED_PACKET_REPEAT_GRAB(14008,"请勿重复抢红包" ),
    RED_PACKET_CREATE_FAILED(14009,"红包创建失败，请重试" ),
    RED_PACKET_LIMIT_EXCEEDED(14010,"红包金额或份数超出限制" ),
    RED_PACKET_AMOUNT_TOO_SMALL(14011,"红包金额太小，每份至少 1 分" ),
    WALLET_DISABLED(14012,"钱包已被禁用" ),
    CHAT_NOT_JOINED(14013,"你不在该会话中，无法发送红包" ),
    GRAB_BUSY(14014,"操作太频繁，请稍后再试" );




    private int code;

    private String msg;

    RedPacketApiCode(int code,String msg){
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
