package com.link.im.enums.group;

import com.link.im.enums.BaseEnum;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月26日
 */
public enum GroupInfoCode implements BaseEnum {

    GROUP_CREATE_SUCCESS(12000,"群聊创建成功");

    private int code;

    private String msg;

    GroupInfoCode(int code,String msg)
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
