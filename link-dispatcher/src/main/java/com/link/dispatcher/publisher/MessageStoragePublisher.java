package com.link.dispatcher.publisher;

import com.link.common.constants.publisher.PublisherRouterKeys;
import com.link.base.entity.message.DefaultMessageInfo;
import com.link.base.entity.message.GroupMessageInfo;
import com.link.base.vo.DefaultMessageVO;
import com.link.base.vo.GroupMessageVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 消息落库发布端（Netty 实时路径用）。
 *
 * <p>实时链路只做「Redis 分配 seq → 推送 → 回 ACK」，落库投到
 * {@code default/group.message.storage.queue}，由 {@code PushMessageStorageListener} 以可控速率消化。
 * 这样 Mongo 的写入速率与瞬时流量解耦——压测打满的是 MQ 队列（磁盘、可堆积），
 * 而不是公网单节点 Mongo 的连接池。
 *
 * <p>原先 restapi 的 HTTP 路径已经在走这条队列（{@code RemotePushPublisher.pushMessageStorage}），
 * 本类是同一条路的 Netty 侧入口。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月23日
 */
@Slf4j
@Component
public class MessageStoragePublisher {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 投递单聊消息落库。
     *
     * @return true 投递成功；false 投递失败（调用方据此决定是否回 ACK）
     */
    public boolean publishDefault(DefaultMessageInfo message) {
        try {
            DefaultMessageVO vo = DefaultMessageVO.from(message);
            vo.setBaseData(null);
            this.rabbitTemplate.convertAndSend(
                    PublisherRouterKeys.MESSAGE_STORAGE_EXCHANGE,
                    PublisherRouterKeys.DEFAULT_MESSAGE_STORAGE_ROUTING_KEY,
                    vo);
            return true;
        } catch (Exception e) {
            log.error("投递单聊消息落库失败: msgId={}, chatId={}",
                    message.getId(), message.getChatId(), e);
            return false;
        }
    }

    /**
     * 投递群消息落库。
     *
     * @return true 投递成功；false 投递失败
     */
    public boolean publishGroup(GroupMessageInfo message) {
        try {
            GroupMessageVO vo = GroupMessageVO.from(message);
            vo.setBaseData(null);
            this.rabbitTemplate.convertAndSend(
                    PublisherRouterKeys.MESSAGE_STORAGE_EXCHANGE,
                    PublisherRouterKeys.GROUP_MESSAGE_STORAGE_ROUTING_KEY,
                    vo);
            return true;
        } catch (Exception e) {
            log.error("投递群消息落库失败: msgId={}, chatId={}",
                    message.getId(), message.getChatId(), e);
            return false;
        }
    }
}
