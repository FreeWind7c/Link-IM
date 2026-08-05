package com.link.common.redis;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月18日
 */
public class RedisKeys {

    /** 会话级 seq 计数器前缀，拼接 chatId。权威来源，INCR 分配；当前值即最新一条消息的 seq。 */
    public static final String SEQ = "seq:";

    public static final String CHAT_SESSION_MEMBER = "chat_session_member:" ;

    /** 红包预扣队列（List），拼 packetId。发红包时拆好的每一份入队，抢红包 rightPop 即预占。 */
    public static final String RED_PACKET = "red_packet:";

    /**
     * 抢红包互斥锁，拼 packetId:userId。
     *
     * <p>Redisson 的锁在 Redis 里是 <b>hash</b> 类型，且持锁期间该 key 必然存在。
     * 绝不能和下面的幂等键用同一个 key —— 同名时持锁中执行 {@code SET key v NX} 必然返回 false，
     * 表现为「所有人第一次抢都被判定成重复请求」。
     */
    public static final String RED_PACKET_GRAB_LOCK = "red_packet:grab:lock:";

    /** 抢红包幂等键（String），拼 packetId:userId。 */
    public static final String RED_PACKET_GRAB_IDEM = "red_packet:grab:idem:";

    /** 发红包幂等键（String），拼客户端传来的 bizDetailId。 */
    public static final String RED_PACKET_SEND_IDEM = "red_packet:send:idem:";

    public static final String USER_TOKEN = "user_token:";
}
