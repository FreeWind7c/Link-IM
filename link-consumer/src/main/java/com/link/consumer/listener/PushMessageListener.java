package com.link.consumer.listener;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.link.common.constants.publisher.PublisherRouterKeys;
import com.link.im.entity.base.BaseMessage;
import com.link.im.entity.chat.ChatSession;
import com.link.im.entity.message.DefaultMessageInfo;
import com.link.im.entity.message.GroupMessageInfo;
import com.link.im.entity.message.type.MessageType;
import com.link.im.sender.LinkMessageSender;
import com.link.im.vo.DefaultMessageVO;
import com.link.im.vo.GroupMessageVO;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
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
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月17日
 */
@Slf4j
@Component
public class PushMessageListener {


    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private LinkMessageSender sender;

    @RabbitListener(queues = PublisherRouterKeys.DEFAULT_MESSAGE_STORAGE_QUEUE , ackMode = "MANUAL")
    public void handle0(Message message, Channel channel) throws IOException {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try{
            DefaultMessageVO vo = new Gson().fromJson(body, DefaultMessageVO.class);
            DefaultMessageInfo defaultMessageInfo = vo.to();
            try {
                this.mongoTemplate.insert(defaultMessageInfo);
            } catch (DuplicateKeyException e) {
                // 已入库（投递重试 / 客户端重发）：幂等成功，继续更新会话并 ack，不能 nack 重投
                log.debug("消息已存在，跳过插入: msgId={}", defaultMessageInfo.getId());
            }
            updateSession(defaultMessageInfo,1);
            channel.basicAck(deliveryTag,false);
        } catch (JsonSyntaxException | IllegalArgumentException e) {
            // 报文本身坏了（JSON 畸形、ObjectId 非法）：重投多少次都一样，直接丢弃
            log.error("消息报文非法，丢弃: body={}", body, e);
            channel.basicNack(deliveryTag, false, false);
        } catch (Exception e) {
            // Mongo 挂了 / 超时等可恢复故障：必须 requeue，否则这批消息永久丢失。
            // 这里是唯一的落库路径，丢了就是真丢了。
            log.error("消息落库失败，重新入队: msgId={}", safeMsgId(body), e);
            channel.basicNack(deliveryTag, false, true);
        }
    }

    @RabbitListener(queues = PublisherRouterKeys.GROUP_MESSAGE_STORAGE_QUEUE , ackMode = "MANUAL")
    public void handle1(Message message, Channel channel) throws IOException {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try{
            GroupMessageVO vo = new Gson().fromJson(body, GroupMessageVO.class);
            GroupMessageInfo groupMessageInfo = vo.to();

            try {
                this.mongoTemplate.insert(groupMessageInfo);
            } catch (DuplicateKeyException e) {
                log.debug("群消息已存在，跳过插入: msgId={}", groupMessageInfo.getId());
            }
            updateSession(groupMessageInfo,2);
            channel.basicAck(deliveryTag,false);
        } catch (JsonSyntaxException | IllegalArgumentException e) {
            log.error("群消息报文非法，丢弃: body={}", body, e);
            channel.basicNack(deliveryTag, false, false);
        } catch (Exception e) {
            log.error("群消息落库失败，重新入队: msgId={}", safeMsgId(body), e);
            channel.basicNack(deliveryTag, false, true);
        }
    }

    /** 出错日志里带上 msgId，但解析失败不能再抛异常盖掉原始错误。 */
    private String safeMsgId(String body) {
        try {
            return new Gson().fromJson(body, DefaultMessageVO.class).getId();
        } catch (Exception ignore) {
            return "unknown";
        }
    }

    private void updateSession(BaseMessage message, int type) {
        // 字段名必须和 ChatSession 上的 @Field 一致：last_msg_summary / last_msg_type。
        // 原来写的是 msg_summary / msg_type，等于往文档里塞了两个没人读的野字段，
        // 而会话列表真正读的 last_msg_* 永远不更新（列表摘要不刷新的根因）。
        //
        // last_msg_seq < 本条 seq 作为条件：MQ 不保证消费顺序，没有这个 guard，
        // 晚到的旧消息会把 lastMsgSeq 改回去，未读数（lastMsgSeq - lastReadSeq）就会算错。
        // 条件不匹配时是无害的 no-op。
        Query query = new Query(
                Criteria.where("chat_id").is(message.getChatId())
                        .and("last_msg_seq").lt(message.getSeq())
        );
        Update update = new Update()
                .set("last_msg_summary", MessageType.summaryOf(message.getType(), message.getBaseData()))
                .set("last_msg_type", message.getType())
                .set("last_msg_time", System.currentTimeMillis())
                .set("last_msg_seq", message.getSeq());
        this.mongoTemplate.updateFirst(query,update,ChatSession.class);
    }

}
