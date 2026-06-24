package com.link.im.enums.gloabl;

import com.link.im.enums.BaseEnum;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月20日
 */
public enum GlobalCode implements BaseEnum {

    GLOBAL_ERROR(0,"系统错误,请联系客服"),
    PARAMETER_VALIDATOR_ERROR(1,"参数校验失败，值不正确或类型错误");

    private int code;

    private String message;


    GlobalCode(int code,String message){
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() {
        return this.code;
    }

    @Override
    public String getMessage() {
        return this.message;
    }
}
