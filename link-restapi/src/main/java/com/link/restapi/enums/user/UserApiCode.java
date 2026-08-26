package com.link.restapi.enums.user;

import com.link.restapi.enums.BaseEnum;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月20日
 */
public enum UserApiCode implements BaseEnum {


    USER_DOES_NOT_EXIST(10000,"用戶不存在"),
    USER_PASSWORD_ERROR(10001, "账号或密码错误"),
    ACCOUNT_ON_ANOTHER_DEVICE(10003, "账号已在其他设备登录"),
    USER_EXIST(10004, "账号已存在"), PASSWORD_NOT_MATCH(10005 ,"两次密码不一致" );

    public static final String LOGIN_SUCCESS = "登录成功";
    public static final String REGISTER_SUCCESS = "注册成功";

    private int code;

    private String message;

    UserApiCode(int code, String message){
        this.code = code;
        this.message = message;
    }


    @Override
    public int getCode(){
        return this.code;
    }

    @Override
    public String getMessage() {
        return this.message;
    }


}
