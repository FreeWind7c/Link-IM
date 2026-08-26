package com.link.common.constants.publisher;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月17日
 */
public class PublisherRouterKeys {

    // 处理消息交换机
    public static final String MESSAGE_EXCHANGE = "message.exchange";

    // 消息入库队列
    public static final String DEFAULT_MESSAGE_STORAGE_QUEUE = "default.message.storage.queue";
    public static final String GROUP_MESSAGE_STORAGE_QUEUE = "group.message.storage.queue";

    // 消息入库Key
    public static final String DEFAULT_MESSAGE_STORAGE_ROUTING_KEY = "default.message.storage.key";
    public static final String GROUP_MESSAGE_STORAGE_ROUTING_KEY = "group.message.storage.key";

}
