package com.link.dispatcher.handler;

import com.google.gson.Gson;
import com.link.common.core.model.ack.LinkAck;
import com.link.core.config.LinkCoreConfig;
import com.link.common.core.event.EventType;
import com.link.core.event.handler.EventHandler;

import com.link.base.seq.MessageSeqAllocator;
import com.link.base.mongo.BasePlatFormMongoService;
import com.link.base.entity.message.DefaultMessageInfo;
import com.link.base.entity.message.quote.QuoteRef;
import com.link.base.entity.message.type.MessageType;
import com.link.dispatcher.processor.message.LinkDefaultMessageProcessor;
import com.link.dispatcher.publisher.MessageHandlerPublisher;
import com.link.dispatcher.publisher.MessageStoragePublisher;

import com.link.base.repository.ChatSessionRepository;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

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
    private ChatSessionRepository sessionRepository;

    @Autowired
    @Qualifier("IMExecutor")
    private ThreadPoolTaskExecutor workerPool;

    /** 落库投递：实时链路不再同步写 Mongo，改投 MQ 由消费端限速消化。 */
    @Autowired
    private MessageStoragePublisher storagePublisher;

    /** 消息处理投递：通过 MQ 分区队列处理消息，确保同一 chatId 的消息顺序性。 */
    @Autowired
    private MessageHandlerPublisher handlerPublisher;

    private HashMap<String,Integer> map = new HashMap<>();


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

        MessageSeqAllocator.SeqResult seqResult = messageSeqAllocator.allocate(
            message.getChatId(),
            message.getId().toHexString()
        );

        int seq = seqResult.duplicate() ? (int) this.messageSeqAllocator.getMessageSeq(
                message.getChatId(),
                message.getId().toHexString()
        ) : (int) seqResult.seq();
        message.setSeq(seq);



        if (this.config.isPartitionConsumption())
        {
            Integer count = map.get(message.getId().toHexString());
            if (count==null)
            {

                submitHeavyViaMQ(message,channel,message.getRcvId().toHexString());
                map.put(message.getId().toHexString(),1);
            }
        }
        else
            submitHeavy(message, channel);
    }


    private void submitHeavy(DefaultMessageInfo message, Channel ch) {

        try {
            this.workerPool.submit(() -> {
                processHeavy(message, ch);
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // 有了上面的限流，走到这里说明是全局性过载而非单会话洪水
            log.error("IM线程池过载，丢弃消息待客户端重发: msgId={}, chatId={}",
                    message.getId(),message.getChatId());
            cleanupDedupKey(message.getChatId(), message.getId().toHexString());
        }
    }


    public void submitHeavyViaMQ(DefaultMessageInfo message, Channel channel, String userId) {
        try {
            boolean success = this.handlerPublisher.publishWithHash(message, channel);
            if (!success) {
                log.error("消息投递到 MQ 失败，等待客户端重发: msgId={}, chatId={}",
                        message.getId(), message.getChatId());
                cleanupDedupKey(message.getChatId(), message.getId().toHexString());
            }
        } catch (Exception e) {
            log.error("消息投递到 MQ 异常: msgId={}, chatId={}",
                    message.getId(), message.getChatId(), e);
            cleanupDedupKey(message.getChatId(), message.getId().toHexString());
        }
    }


    public void processHeavy(DefaultMessageInfo message, Channel channel) {

        long start = System.currentTimeMillis();
        messageProcessor.sanitizeQuote(message);
        long afterSanitize = System.currentTimeMillis();

        try{
            this.insert(message);
        }catch (DuplicateKeyException e){
            log.error("消息入库ID重复 -> {}",message.getId().toHexString());
            return;
        }

        long afterPush = System.currentTimeMillis();
        this.messageProcessor.processor(message, channel);
        long afterPublish = System.currentTimeMillis();

        this.config.getLinkSender().send(EventType.ACK, channel, new LinkAck(
                message.getId().toHexString(),
                message.getChatId(),
                message.getSeq()
        ));


        this.sessionRepository.updateSession(message);

        long end = System.currentTimeMillis();

        log.info("耗时分解 - sanitize:{}ms, push:{}ms, mqPublish:{}ms, ack:{}ms, 总计:{}ms",
                (afterSanitize - start) ,
                (afterPush - afterSanitize) ,
                (afterPublish - afterPush) ,
                (end - afterPublish) ,
                (end - start) );
    }




    /**
     * 清除 Redis 去重标记，允许客户端重试
     */
    private void cleanupDedupKey(String chatId, String msgId) {
        try {
            this.messageSeqAllocator.releaseDedup(chatId, msgId);
        } catch (Exception e) {
            log.error("清除去重标记失败: chatId={}, msgId={}", chatId, msgId, e);
        }
    }
}
