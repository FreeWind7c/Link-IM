package com.link.dispatcher.listener;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.link.common.constants.publisher.PublisherRouterKeys;
import com.link.base.entity.base.BaseMessage;
import com.link.base.entity.chat.ChatSession;
import com.link.base.entity.message.DefaultMessageInfo;
import com.link.base.entity.message.GroupMessageInfo;
import com.link.base.entity.message.type.MessageType;
import com.link.dispatcher.sender.LinkMessageSender;
import com.link.base.vo.DefaultMessageVO;
import com.link.base.vo.GroupMessageVO;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 消息存储监听器
 *
 * <p>消费 MQ 消息并完成：
 * 1. 消息入库（MongoDB）
 * 2. 更新会话信息（最后一条消息摘要、时间、seq）
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月17日
 */
@Slf4j
@Component
public class PushMessageStorageListener {


    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private LinkMessageSender sender;

    /**
     * 处理普通消息（单聊）
     */
    @RabbitListener(queues = PublisherRouterKeys.DEFAULT_MESSAGE_STORAGE_QUEUE)
    public void handleDefaultMessage(Message message, Channel channel) throws IOException {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            DefaultMessageVO vo = new Gson().fromJson(body, DefaultMessageVO.class);
            DefaultMessageInfo defaultMessageInfo = vo.to();
            try {
                this.mongoTemplate.insert(defaultMessageInfo);
            } catch (DuplicateKeyException e) {
                log.debug("消息已存在，跳过插入: msgId={}", defaultMessageInfo.getId());
            }
            updateSession(defaultMessageInfo, 1);
            channel.basicAck(deliveryTag, false);
        } catch (JsonSyntaxException | IllegalArgumentException e) {
            log.error("消息报文非法，丢弃: body={}", body, e);
            channel.basicNack(deliveryTag, false, false);
        } catch (Exception e) {
            log.error("消息落库失败，重新入队: msgId={}", safeMsgId(body), e);
            channel.basicNack(deliveryTag, false, true);
        }
    }

    /**
     * 处理群消息
     */
    @RabbitListener(queues = PublisherRouterKeys.GROUP_MESSAGE_STORAGE_QUEUE)
    public void handleGroupMessage(Message message, Channel channel) throws IOException {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            GroupMessageVO vo = new Gson().fromJson(body, GroupMessageVO.class);GroupMessageInfo groupMessageInfo = vo.to();
            try {
                this.mongoTemplate.insert(groupMessageInfo);
            } catch (DuplicateKeyException e) {
                log.debug("群消息已存在，跳过插入: msgId={}", groupMessageInfo.getId());
            }
            updateSession(groupMessageInfo, 2);
            channel.basicAck(deliveryTag, false);
        } catch (JsonSyntaxException | IllegalArgumentException e) {
            log.error("群消息报文非法，丢弃: body={}", body, e);
            channel.basicNack(deliveryTag, false, false);
        } catch (Exception e) {
            log.error("群消息落库失败，重新入队: msgId={}", safeMsgId(body), e);
            channel.basicNack(deliveryTag, false, true);
        }
    }

    private String safeMsgId(String body) {
        try {
            return new Gson().fromJson(body, DefaultMessageVO.class).getId();
        } catch (Exception ignore) {
            return "unknown";
        }
    }

    private void updateSession(BaseMessage message, int type) {
        Query query = new Query(
                Criteria.where("chat_id").is(message.getChatId())
                        .and("last_msg_seq").lt(message.getSeq())
        );
        Update update = new Update()
                .set("last_msg_summary", MessageType.summaryOf(message.getType(), message.getBaseData()))
                .set("last_msg_type", message.getType())
                .set("last_msg_time", System.currentTimeMillis())
                .set("last_msg_seq", message.getSeq());
        this.mongoTemplate.updateFirst(query, update, ChatSession.class);
    }

}
