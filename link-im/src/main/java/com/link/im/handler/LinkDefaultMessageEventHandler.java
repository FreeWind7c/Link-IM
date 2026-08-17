package com.link.im.handler;

import com.google.gson.Gson;
import com.link.common.core.model.ack.LinkAck;
import com.link.core.config.LinkCoreConfig;
import com.link.common.core.event.EventType;
import com.link.core.event.handler.EventHandler;

import com.link.core.util.seq.MessageSeqAllocator;
import com.link.im.entity.base.BaseData;
import com.link.im.mongo.BasePlatFormMongoService;
import com.link.im.entity.chat.ChatSession;
import com.link.im.entity.message.DefaultMessageInfo;
import com.link.im.entity.message.quote.QuoteRef;
import com.link.im.entity.message.type.MessageType;
import com.link.im.processor.message.LinkDefaultMessageProcessor;

import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月17日
 */
@Slf4j
@Component
public class LinkDefaultMessageEventHandler extends BasePlatFormMongoService<DefaultMessageInfo> implements EventHandler  {

    @Autowired
    private LinkCoreConfig config;

    @Autowired
    private MessageSeqAllocator messageSeqAllocator;


    @Autowired
    private LinkDefaultMessageProcessor messageProcessor;


    @Autowired
    @Qualifier("IMExecutor")
    private ThreadPoolTaskExecutor workerPool;


    @Override
    public EventType event() {
        return EventType.DEFAULT_MESSAGE;
    }

    @Override
    public Class<?> bodyClass() {
        return DefaultMessageInfo.class;
    }

    @Override
    public String partitionKey(Object obj) {
        return ((DefaultMessageInfo) obj).getChatId();
    }

    @Override
    public void handler(Object obj, Channel channel) {
        DefaultMessageInfo message = (DefaultMessageInfo) obj;
        log.info("message:" + new Gson().toJson(message));
        MessageSeqAllocator.SeqResult seqResult = messageSeqAllocator.allocate(message.getChatId(), message.getId().toHexString());
        if (seqResult.duplicate())
        {
            this.config.getLinkSender().send(EventType.ACK,channel,message.getSeq());
            return;
        }
        message.setSeq((int) seqResult.seq());

        this.workerPool.submit(() -> processHeavy(message,channel));
    }



    private void processHeavy(DefaultMessageInfo message,  Channel channel) {
        // 引用校验：回查原消息、校验可引用性、用服务端快照覆盖客户端传值，防伪造。入库前完成。
        sanitizeQuote(message);
        LinkAck linkAck = new LinkAck(message.getId().toHexString(),message.getChatId(),message.getSeq());
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
                .set(col(ChatSession::getLastMsgSummary), MessageType.summaryOf(message.getType(), message.getBaseData()))
                .set(col(ChatSession::getLastMsgType), message.getType())
                .set(col(ChatSession::getLastMsgTime), now())
                .set(col(ChatSession::getLastMsgSeq), message.getSeq());
        this.getMongoTemplate().updateFirst(eq, update,ChatSession.class);

        this.config.getLinkSender().send(EventType.ACK,channel,linkAck);
        this.messageProcessor.processor(message,channel);
    }


    private void sanitizeQuote(DefaultMessageInfo message) {
        QuoteRef quote = message.getQuote();
        if (quote == null) {
            return;
        }
        // 引用只允许指向本会话内的消息；seq 缺失无法定位
        if (quote.getSeq() <= 0 || !message.getChatId().equals(quote.getChatId())) {
            message.setQuote(null);
            return;
        }

        Query srcQuery = eq(
                where(col(DefaultMessageInfo::getChatId)).is(message.getChatId())
                        .and(col(DefaultMessageInfo::getSeq)).is(quote.getSeq())
        );
        DefaultMessageInfo source = this.findOne(srcQuery);
        if (source == null || !MessageType.isQuotable(source.getType())) {
            message.setQuote(null);
            return;
        }
        message.setQuote(QuoteRef.of(source, message.getBaseData()));
    }
}
