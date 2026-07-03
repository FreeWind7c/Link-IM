package com.link.util.delivery;

import io.netty.channel.Channel;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月18日
 *
 */
@Component
public class MessageRetryManager {

    /** 全局唯一时间轮：tick 100ms，512 槽。海量短超时任务正是它的设计场景。 */
    private final HashedWheelTimer timer =
            new HashedWheelTimer(100, TimeUnit.MILLISECONDS, 512);


    private final ConcurrentHashMap<String, Timeout> pending = new ConcurrentHashMap<>();


    public void schedule(String messageId, Channel channel, long delayMs, TimerTask task) {
        String key = key(messageId, channel);
        Timeout timeout = this.timer.newTimeout(task, delayMs, TimeUnit.MILLISECONDS);
        Timeout old = this.pending.put(key, timeout);
        if (old != null) {
            old.cancel();
        }
    }


    public void ack(String messageId, Channel channel) {
        Timeout t = this.pending.remove(key(messageId, channel));
        if (t != null) {
            t.cancel();
        }
    }

    public void remove(String messageId, Channel channel) {
        this.pending.remove(key(messageId, channel));
    }

    private String key(String messageId, Channel channel) {
        return messageId + ":" + channel.id().asLongText();
    }

    @PreDestroy
    public void shutdown() {
        this.timer.stop();
    }
}
