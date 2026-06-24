package com.link.im.processor.message;

import com.link.core.config.LinkCoreConfig;
import com.link.common.core.event.EventType;
import com.link.core.session.service.LinkSession;
import com.link.im.entity.message.AbstractMessage;
import com.link.im.entity.message.DefaultMessageInfo;
import com.link.im.processor.LinkMessageProcessor;
import com.link.util.delivery.MessageRetryManager;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
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
public class LinkDefaultMessageProcessor implements LinkMessageProcessor {

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
    public void processor(AbstractMessage abstractMessage, Channel channel) {
        DefaultMessageInfo message = (DefaultMessageInfo) abstractMessage;
        forwardSend(message);
    }

    private void forwardSend(DefaultMessageInfo message) {

        String rcvId = message.getRcvId();
        List<LinkSession> sessions = this.config.getSessionManager().getSession(rcvId);

        // B 完全离线：不推送，消息保持 undelivered，等 B 上线同步时再补送。
        if (sessions == null || sessions.isEmpty()) {
            log.info("接收方 {} 不在线，消息 {} 留存待上线同步", rcvId, message.getId());
            return;
        }

        // 逐个在线端推送，并为每个端各挂一个超时重发任务（多端各自独立确认）。
        for (LinkSession session : sessions) {
            Channel target = session.getChannel();
            if (target == null || !target.isActive()) {
                continue;
            }
            pushWithRetry(message, target, 1);
        }


    }

    /**
     * 推送一次并登记超时重发。
     *
     * @param attempt 本次是第几次发送（1 = 首发）
     */
    private void pushWithRetry(DefaultMessageInfo message, Channel target, int attempt) {
        this.config.getLinkSender().send(EventType.DEFAULT_MESSAGE, target, message);

        long delay = BASE_DELAY_MS * (1L << (attempt - 1));   // 3s, 6s, 12s, 24s...
        this.retryManager.schedule(message.getId(), target, delay, timeout -> {
            // 跑到这里说明超时仍未收到 B 的 ACK（收到的话该任务早被 cancel 了）

            // 对端已掉线：停止在线重发，交给上线同步。
            if (!target.isActive()) {
                this.retryManager.remove(message.getId(), target);
                log.info("接收端 {} 已掉线，停止重发消息 {}", target.id(), message.getId());
                return;
            }

            // 达到上限：放弃在线重发，消息仍是 undelivered，靠 B 上线同步兜底。
            if (attempt >= MAX_ATTEMPTS) {
                this.retryManager.remove(message.getId(), target);
                log.warn("消息 {} 重发达上限 {} 次仍未确认，转上线同步兜底", message.getId(), MAX_ATTEMPTS);
                return;
            }

            log.info("消息 {} 第 {} 次重发到 {}", message.getId(), attempt + 1, target.id());
            pushWithRetry(message, target, attempt + 1);   // 重发 + 重新挂下一次超时
        });
    }
}
