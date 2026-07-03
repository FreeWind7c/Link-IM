package com.link.common.redis;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月18日
 */
public class RedisConstant {

    /** 会话级 seq 计数器前缀，拼接 chatId。权威来源，INCR 分配；当前值即最新一条消息的 seq。 */
    public static final String SEQ = "seq:";

    public static final String CHAT_SESSION_MEMBER = "chat_session_member:" ;
}
