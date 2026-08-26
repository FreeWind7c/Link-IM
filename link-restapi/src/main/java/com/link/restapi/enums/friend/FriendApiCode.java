package com.link.restapi.enums.friend;

import com.link.restapi.enums.BaseEnum;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月21日
 */
public enum FriendApiCode implements BaseEnum {

    FRIEND_DOES_NOT_EXIST(11000,"用户不存在"),
    FRIEND_EXIST(11001, "您与该用户已是好友"),
    DONT_ADD_SELF(11002,"无法添加自己为好友" );


    public static final String NOTIFY_USER = "已发送通知给用户";
    private int code;

    private String message;


    FriendApiCode(int code, String message){
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
