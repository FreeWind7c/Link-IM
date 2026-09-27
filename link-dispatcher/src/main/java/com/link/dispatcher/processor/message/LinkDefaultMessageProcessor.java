package com.link.dispatcher.processor.message;

import com.link.base.entity.message.quote.QuoteRef;
import com.link.base.entity.message.type.MessageType;
import com.link.base.mongo.BasePlatFormMongoService;
import com.link.core.config.LinkCoreConfig;
import com.link.common.core.event.EventType;
import com.link.core.session.service.LinkSession;
import com.link.base.entity.base.BaseMessage;
import com.link.base.entity.message.DefaultMessageInfo;
import com.link.core.util.delivery.MessageRetryManager;
import com.link.dispatcher.processor.LinkMessageProcessor;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;


/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月18日
 *
 * 处理 S->C 转发：推送给接收方 B 的每个在线端，并挂超时重发任务等待 B 的 ACK。
 */
@Slf4j
@Component
public class LinkDefaultMessageProcessor extends BasePlatFormMongoService<DefaultMessageInfo> implements LinkMessageProcessor  {

    /** 最大重发次数，超过则放弃在线重发（留给 B 上线同步兜底）。 */
    private static final int MAX_ATTEMPTS = 5;

    /** 首次重发延迟（毫秒），之后按 2 的幂指数退避。 */
    private static final long BASE_DELAY_MS = 3000;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private MessageRetryManager retryManager;

    @Autowired
    private LinkCoreConfig config;



    @Override
    public void processor(BaseMessage message, Channel channel) {
        DefaultMessageInfo info = (DefaultMessageInfo) message;
        // 根据 rcvId 转发给接收者
        forwardToReceiver(info);
    }

    /**
     * 根据 rcvId 转发消息给接收者
     * @param message 消息体
     */
    public void forwardToReceiver(DefaultMessageInfo message) {
        String rcvId = message.getRcvId().toHexString();
        forwardSend(message, rcvId);
    }

    public void sanitizeQuote(DefaultMessageInfo message) {
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
     * 根据 rcvId 转发消息给接收者的所有在线端
     * @param message 消息体
     * @param rcvId 接收者ID
     */
    public void forwardSend(DefaultMessageInfo message, String rcvId) {
        List<LinkSession> sessions = this.config.getSessionManager().getSession(rcvId);

        if (sessions == null || sessions.isEmpty()) {
            log.info("接收方 {} 不在线，消息 {} 留存待上线同步", rcvId, message.getId());
            return;
        }

        // 转换为 DTO，避免 ObjectId 序列化问题
        com.link.base.dto.message.DefaultMessageDTO messageDTO = message.toDTO();

        // 逐个在线端推送，并为每个端各挂一个超时重发任务（多端各自独立确认）。
        for (LinkSession session : sessions) {
            Channel target = session.getChannel();
            if (target == null || !target.isActive()) {
                log.debug("接收方 {} 的某个会话不活跃，跳过转发 | msgId={}", rcvId, message.getId());
                continue;
            }
            pushWithRetry(messageDTO, message.getId().toHexString(), target, 1);
        }
    }

    /**
     * 推送一次并登记超时重发。
     *
     * @param messageDTO 消息DTO（已转换为String类型ID）
     * @param messageId 消息ID（String类型）
     * @param target 目标channel
     * @param attempt 本次是第几次发送（1 = 首发）
     */
    public void pushWithRetry(com.link.base.dto.message.DefaultMessageDTO messageDTO, String messageId, Channel target, int attempt) {
        this.config.getLinkSender().send(EventType.DEFAULT_MESSAGE, target, messageDTO);
        long delay = BASE_DELAY_MS * (1L << (attempt - 1));   // 3s, 6s, 12s, 24s...
        this.retryManager.schedule(messageId, target, delay, timeout -> {
            // 跑到这里说明超时仍未收到 B 的 ACK（收到的话该任务早被 cancel 了）

            // 对端已掉线：停止在线重发，交给上线同步。
            if (!target.isActive()) {
                this.retryManager.remove(messageId, target);
                log.info("接收端 {} 已掉线，停止重发消息 {}", target.id(), messageId);
                return;
            }

            // 达到上限：放弃在线重发，消息仍是 undelivered，靠 B 上线同步兜底。
            if (attempt >= MAX_ATTEMPTS) {
                this.retryManager.remove(messageId, target);
                log.warn("消息 {} 重发达上限 {} 次仍未确认，转上线同步兜底", messageId, MAX_ATTEMPTS);
                return;
            }

            log.info("消息 {} 第 {} 次重发到 {}", messageId, attempt + 1, target.id());
            pushWithRetry(messageDTO, messageId, target, attempt + 1);   // 重发 + 重新挂下一次超时
        });
    }
}
