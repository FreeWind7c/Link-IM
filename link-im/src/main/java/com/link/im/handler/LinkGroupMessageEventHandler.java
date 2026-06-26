package com.link.im.handler;

import com.link.common.core.event.EventType;
import com.link.common.core.model.ack.LinkAck;
import com.link.core.config.LinkCoreConfig;
import com.link.core.event.handler.EventHandler;
import com.link.core.util.seq.MessageSeqAllocator;
import com.link.im.entity.chat.ChatSession;
import com.link.im.entity.message.DefaultMessageInfo;
import com.link.im.entity.message.GroupMessageInfo;
import com.link.im.entity.message.type.MessageType;
import com.link.im.mongo.BasePlatFormMongoService;
import com.link.im.processor.message.LinkGroupMessageProcessor;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月18日
 */
@Slf4j
@Component
public class LinkGroupMessageEventHandler extends BasePlatFormMongoService<GroupMessageInfo> implements EventHandler {

    @Autowired
    private LinkCoreConfig config;

    @Autowired
    private MessageSeqAllocator messageSeqAllocator;


    @Autowired
    private LinkGroupMessageProcessor messageProcessor;

    @Override
    public EventType event() {
        return EventType.GROUP_MESSAGE;
    }

    @Override
    public Class<?> bodyClass() {
        return GroupMessageInfo.class;
    }

    @Override
    public void handler(Object obj, Channel channel) {
        GroupMessageInfo message = (GroupMessageInfo) obj;
        MessageSeqAllocator.SeqResult seqResult = messageSeqAllocator.allocate(message.getChatId(), message.getId());
        printf("message:",message, DefaultMessageInfo.class);
        if (seqResult.duplicate())
        {
            this.config.getLinkSender().send(EventType.ACK,channel,message.getSeq());
            return;
        }
        message.setSeq((int) seqResult.seq());
        LinkAck linkAck = new LinkAck(message.getId(),message.getChatId(),message.getSeq());
        try {
            this.insert(message);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // Redis 去重漏网的重发:DB 已有,捞出已存在那条,回 ACK 即可,不再转发
            this.config.getLinkSender().send(EventType.ACK, channel, linkAck);
            return;
        }

        Query eq = eq(
                where(col(ChatSession::getChatId)).is(message.getChatId())
                        .and(col(ChatSession::getLastMsgSeq)).lt(message.getSeq())
        );

        Update update = update()
                .set(col(ChatSession::getLastMsgSummary), MessageType.summaryOf(message.getType(), message.getData()))
                .set(col(ChatSession::getLastMsgType), message.getType())
                .set(col(ChatSession::getLastMsgTime), now())
                .set(col(ChatSession::getLastMsgSeq), message.getSeq());


        this.getMongoTemplate().updateFirst(eq, update,ChatSession.class);
        this.config.getLinkSender().send(EventType.ACK,channel,linkAck);
        this.messageProcessor.processor(message,channel);
    }
}
