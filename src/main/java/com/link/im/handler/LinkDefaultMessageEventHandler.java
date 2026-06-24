package com.link.im.handler;

import com.link.core.config.LinkCoreConfig;
import com.link.core.event.EventType;
import com.link.core.event.handler.EventHandler;
import com.link.core.model.ack.LinkAck;
import com.link.im.common.mongo.BaseMongoService;
import com.link.im.entity.chat.ChatSession;
import com.link.im.entity.message.DefaultMessageInfo;
import com.link.im.entity.message.type.MessageType;
import com.link.im.processor.message.LinkDefaultMessageProcessor;
import com.link.util.print.LinkPrintJsonUtil;
import com.link.util.seq.MessageSeqAllocator;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月17日
 */
@Slf4j
@Component
public class LinkDefaultMessageEventHandler extends BaseMongoService<DefaultMessageInfo> implements EventHandler  {

    @Autowired
    private LinkCoreConfig config;

    @Autowired
    private MessageSeqAllocator messageSeqAllocator;


    @Autowired
    private LinkDefaultMessageProcessor messageProcessor;


    @Override
    public EventType event() {
        return EventType.DEFAULT_MESSAGE;
    }

    @Override
    public Class<?> bodyClass() {
        return DefaultMessageInfo.class;
    }

    @Override
    public void handler(Object obj, Channel channel) {
        // TODO 该阶段解析消息、生成序列号、入库、回ACK 该handler 处理C->S消息可靠
        DefaultMessageInfo message = (DefaultMessageInfo) obj;
        MessageSeqAllocator.SeqResult seqResult = messageSeqAllocator.allocate(message.getChatId(), message.getId());

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

        // 不 upsert：会话在建会话时已 insert。只更新已存在且 lastMsgSeq < 本条 seq 的记录，
        // 既避免乱序回退，又因为是 set 不是 inc，偶发更新失败会被下一条更大 seq 自愈。
        this.getMongoTemplate().updateFirst(eq, update,ChatSession.class);

        this.config.getLinkSender().send(EventType.ACK,channel,linkAck);
        this.messageProcessor.processor(message,channel);
    }
}
