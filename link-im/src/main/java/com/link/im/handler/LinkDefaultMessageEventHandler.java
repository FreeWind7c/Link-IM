package com.link.im.handler;

import com.link.common.core.model.ack.LinkAck;
import com.link.core.config.LinkCoreConfig;
import com.link.common.core.event.EventType;
import com.link.core.event.handler.EventHandler;

import com.link.core.util.seq.MessageSeqAllocator;
import com.link.im.mongo.BasePlatFormMongoService;
import com.link.im.entity.message.DefaultMessageInfo;
import com.link.im.entity.message.quote.QuoteRef;
import com.link.im.entity.message.type.MessageType;
import com.link.im.processor.message.LinkDefaultMessageProcessor;
import com.link.im.publisher.MessageStoragePublisher;

import com.link.im.repository.ChatSessionRepository;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

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

    private int i = 0;

    @Override
    public void handler(Object obj, Channel channel) {
        // nanoTime 而非 currentTimeMillis：后者在 Windows 上精度约 15.6ms，
        // 本阶段只有一次 Redis EVAL，真实耗时在 1ms 量级，用毫秒钟表量不出来。
        long startNanos = System.currentTimeMillis();
        DefaultMessageInfo message = (DefaultMessageInfo) obj;
        List<Channel> channels = this.config.getSessionManager().getChannel(message.getSndId().toHexString())
                .stream().filter(Channel::isActive).collect(Collectors.toList());
        Channel ch = channels.get(0);
        // 单线程分配 seq
        MessageSeqAllocator.SeqResult seqResult = messageSeqAllocator.allocate(
            message.getChatId(),
            message.getId().toHexString()
        );
        log.info("第一阶段耗时: {}ms, seq={}", (System.currentTimeMillis() - startNanos), message.getSeq());


        long stage2Micros = System.currentTimeMillis();
        int seq = seqResult.duplicate() ? (int) this.messageSeqAllocator.getMessageSeq(
                message.getChatId(),
                message.getId().toHexString()
        ) : (int) seqResult.seq();
        message.setSeq(seq);
        log.info("第二阶段耗时: {}ms, seq={}", (System.currentTimeMillis()-stage2Micros), message.getSeq());

        submitHeavy(message, ch);
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


    public void processHeavy(DefaultMessageInfo message, Channel channel) {
        // 与 handler 的第一阶段统一用 nanoTime + 微秒，两段口径一致才能对比。
        long start = System.currentTimeMillis();
        sanitizeQuote(message);
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
