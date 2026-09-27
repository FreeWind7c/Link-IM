package com.link.restapi.enums.chat;

import com.link.restapi.enums.BaseEnum;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月27日
 */
public enum ChatSessionCode implements BaseEnum {

    CHAT_SESSION_NOT_EXIST(15000,"会话不存在");

    private int code;

    private String message;


    ChatSessionCode(int code, String message){
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
