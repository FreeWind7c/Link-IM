package com.link.im.handler;

import com.link.common.core.model.ack.LinkAck;
import com.link.core.config.LinkCoreConfig;
import com.link.common.core.event.EventType;
import com.link.core.event.handler.EventHandler;

import com.link.core.util.seq.MessageSeqAllocator;
import com.link.im.mongo.BasePlatFormMongoService;
import com.link.im.entity.chat.ChatSession;
import com.link.im.entity.message.AbstractMessage;
import com.link.im.entity.message.DefaultMessageInfo;
import com.link.im.entity.message.QuoteRef;
import com.link.im.entity.message.type.MessageType;
import com.link.im.processor.message.LinkDefaultMessageProcessor;

import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
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

    /** 共享线程池：承接 seq 分配之后的重活（落库、会话更新、扇出推送），并行处理不受分区限制。 */
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

    /** 按 chatId 分区：同一会话的消息恒定进同一单线程，保证 seq 分配严格有序。 */
    @Override
    public String partitionKey(Object obj) {
        return ((DefaultMessageInfo) obj).getChatId();
    }

    @Override
    public void handler(Object obj, Channel channel) {
        // 本方法运行在分区单线程上（同一 chatId 串行）。
        // 只做"必须有序"的最小临界区：分配 seq。分配完立刻把重活甩给共享池并行，
        // 避免慢 IO（落库、推送）占用分区线程，把单会话吞吐拖成瓶颈。
        DefaultMessageInfo message = (DefaultMessageInfo) obj;
        MessageSeqAllocator.SeqResult seqResult = messageSeqAllocator.allocate(message.getChatId(), message.getId());
        printf("message:",message,DefaultMessageInfo.class);
        if (seqResult.duplicate())
        {
            this.config.getLinkSender().send(EventType.ACK,channel,message.getSeq());
            return;
        }
        message.setSeq((int) seqResult.seq());

        // seq 已定，后续均与顺序无关：落库按 seq 查、会话更新有 lt 守卫自愈、推送端上按 seq 排序。
        this.workerPool.submit(() -> processHeavy(message, channel));
    }

    /** 重活：在共享线程池并行执行。引用校验、入库、会话摘要更新、回 ACK、转发推送。 */
    private void processHeavy(DefaultMessageInfo message, Channel channel) {
        // TODO 该阶段解析消息、生成序列号、入库、回ACK 该handler 处理C->S消息可靠
        // 引用校验：回查原消息、校验可引用性、用服务端快照覆盖客户端传值，防伪造。入库前完成。
        sanitizeQuote(message);
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

    /**
     * 校验并重建引用快照。客户端只传 {@code {msgId, seq, chatId}} 定位字段，其余快照由此回查补全，
     * 防止客户端伪造引用内容或引用不可引用类型（红包/语音等）。
     *
     * <p>以下情况清空 quote（当作普通消息处理，不拒收整条）：定位字段缺失、跨会话引用、
     * 原消息不存在、原消息类型不可引用。
     */
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
                where(col(AbstractMessage::getChatId)).is(message.getChatId())
                        .and(col(AbstractMessage::getSeq)).is(quote.getSeq())
        );
        DefaultMessageInfo source = this.findOne(srcQuery);
        if (source == null || !MessageType.isQuotable(source.getType())) {
            message.setQuote(null);
            return;
        }
        // 用服务端回查到的原消息重建快照，整体覆盖客户端传来的内容字段
        message.setQuote(QuoteRef.of(source));
    }
}
