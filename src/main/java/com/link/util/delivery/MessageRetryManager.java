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
 * S->C 投递的待确认 + 超时重发管理器。
 *
 * <p>全局只有一个 HashedWheelTimer（1 个线程 + 一个时间轮 bucket 数组），承载所有
 * 待确认消息的超时任务；每条消息挂一个轻量的 Timeout 句柄，按 messageId+channelId
 * 精确登记/取消，互不干扰。这是 processor（登记）与 ACK handler（取消）之间的共享桥梁。
 */
@Component
public class MessageRetryManager {

    /** 全局唯一时间轮：tick 100ms，512 槽。海量短超时任务正是它的设计场景。 */
    private final HashedWheelTimer timer =
            new HashedWheelTimer(100, TimeUnit.MILLISECONDS, 512);

    /**
     * 待确认表：key = messageId:channelId（精确到“哪条消息发给了哪个设备”），
     * value = 本次超时句柄。多端时同一 messageId 会发给 B 的多个 channel，故 key 带 channelId。
     */
    private final ConcurrentHashMap<String, Timeout> pending = new ConcurrentHashMap<>();

    /**
     * 登记一次超时任务。同 key 的旧任务（上一次重发挂的）会被取消，保证一条消息在一个
     * 设备上同一时刻只有一个有效超时。
     */
    public void schedule(String messageId, Channel channel, long delayMs, TimerTask task) {
        String key = key(messageId, channel);
        Timeout timeout = this.timer.newTimeout(task, delayMs, TimeUnit.MILLISECONDS);
        Timeout old = this.pending.put(key, timeout);
        if (old != null) {
            old.cancel();
        }
    }

    /**
     * 收到 B 对某条消息的 ACK：按 messageId+channel 精确取消并移除。
     * 取的是这一条自己的 Timeout，不可能误伤别条。
     */
    public void ack(String messageId, Channel channel) {
        Timeout t = this.pending.remove(key(messageId, channel));
        if (t != null) {
            t.cancel();
        }
    }

    /** 重发流程自行结束（超次数 / 对端离线）时清理登记，避免 map 泄漏。 */
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
