package com.link.common.constants.publisher;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月17日
 */
public class PublisherRouterKeys {

    // 处理消息交换机
    public static final String MESSAGE_STORAGE_EXCHANGE = "message.storage.exchange";

    // 消息入库队列
    public static final String DEFAULT_MESSAGE_STORAGE_QUEUE = "default.message.storage.queue";
    public static final String GROUP_MESSAGE_STORAGE_QUEUE = "group.message.storage.queue";

    // 消息入库Key
    public static final String DEFAULT_MESSAGE_STORAGE_ROUTING_KEY = "default.message.storage.key";
    public static final String GROUP_MESSAGE_STORAGE_ROUTING_KEY = "group.message.storage.key";

    // ========== 消息处理队列（分区队列，确保顺序性）==========
    // 消息处理交换机
    public static final String MESSAGE_HANDLER_EXCHANGE = "message.handler.exchange";

    // 消息处理队列（4 个分区，基于 chatId 哈希）
    public static final String MESSAGE_HANDLER_QUEUE = "message.handler.queue";


    // 消息处理路由键
    public static final String MESSAGE_HANDLER_ROUTING_KEY = "message.handler.key";

    // 分区数量
    public static final int MESSAGE_HANDLER_PARTITION_COUNT = 4;

    // ========== 死信交换机和队列 ==========
    // 死信交换机（用于接收重试失败的消息）
    public static final String LINK_DEAD_EXCHANGE = "link.dead.exchange";

    // 消息死信队列（处理消息入库失败的情况）
    public static final String LINK_DEAD_MESSAGE_QUEUE = "link.dead.message.queue";
    public static final String LINK_DEAD_MESSAGE_KEY = "link.dead.message.key";

    // 会话死信队列（处理会话更新失败的情况）
    public static final String LINK_DEAD_SESSION_QUEUE = "link.dead.session.queue";
    public static final String LINK_DEAD_SESSION_KEY = "link.dead.session.key";

    // ========== 延迟重试队列 ==========
    // 延迟重试交换机
    public static final String MESSAGE_RETRY_EXCHANGE = "message.retry.exchange";

    // 延迟重试队列（消息过期后会路由回原业务队列）
    public static final String MESSAGE_RETRY_QUEUE = "message.retry.queue";
    public static final String MESSAGE_RETRY_KEY = "message.retry.key";
    public static final String LINK_EVENT_EXCHANGE = "link.event.exchange";
    public static final String LINK_EVENT_PUSH_QUEUE = "link.event.push.queue";
    public static final String DIRECT_EVENT_PUSH_KEY = "link.event.push.key";
}
