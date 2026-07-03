package com.link.core.security;

import com.link.common.channel.DefaultChannelAttributeKeys;
import com.link.common.core.event.EventType;
import com.link.core.config.LinkCoreConfig;
import com.link.core.session.service.LinkSession;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.ScheduledFuture;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月18日
 */
@Slf4j
public class ConnectionSecurityManager {

    private final LinkCoreConfig config;

    /** 每个 IP 当前连接数 */
    private final ConcurrentHashMap<String, Integer> ipConnCount = new ConcurrentHashMap<>();

    /** 全局连接数 */
    private final AtomicInteger totalConn = new AtomicInteger();

    /** 标记该连接是否已计入计数，保证 onDisconnect 只扣减一次 */
    private static final AttributeKey<Boolean> COUNTED_KEY = AttributeKey.valueOf("CONN_COUNTED");

    /** 认证超时任务句柄，登录成功后用它取消 */
    private static final AttributeKey<ScheduledFuture<?>> AUTH_TIMEOUT_KEY = AttributeKey.valueOf("AUTH_TIMEOUT_FUTURE");

    private static final AttributeKey<LinkSession> SESSION_KEY = AttributeKey.valueOf(DefaultChannelAttributeKeys.SESSION);

    public ConnectionSecurityManager(LinkCoreConfig config) {
        this.config = config;
    }

    /**
     * 连接建立时调用：全局 / 单 IP 连接数校验 + 计数 + 安排认证超时。
     */
    public boolean onConnect(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();
        String ip = ip(channel);

        int total = totalConn.incrementAndGet();
        int perIp = ipConnCount.merge(ip, 1, Integer::sum);
        // 标记已计数；关连接后 channelInactive -> onDisconnect 会据此扣减
        channel.attr(COUNTED_KEY).set(Boolean.TRUE);

        if (total > config.getMaxConnections()) {
            log.warn("全局连接数超限({})，拒绝连接 {}", total, channel.id());
            channel.close();
            return false;
        }
        if (perIp > config.getMaxConnPerIp()) {
            log.warn("IP={} 连接数超限({})，拒绝连接 {}", ip, perIp, channel.id());
            channel.close();
            return false;
        }

        scheduleAuthTimeout(ctx);
        return true;
    }

    /**
     * 连接断开时调用：扣减计数 + 取消认证超时任务。
     */
    public void onDisconnect(Channel channel) {
        // 只有计过数的连接才扣减，且 getAndSet 保证只扣一次
        if (channel.attr(COUNTED_KEY).getAndSet(null) == null) {
            return;
        }
        totalConn.decrementAndGet();
        String ip = ip(channel);
        ipConnCount.computeIfPresent(ip, (k, v) -> v <= 1 ? null : v - 1);
        cancelAuthTimeout(channel);
    }

    /**
     * 认证成功后调用：取消认证超时任务，连接从「待认证」转为长期持有。
     */
    public void onAuthSuccess(Channel channel) {
        cancelAuthTimeout(channel);
    }

    /**
     * 帧长度校验：防止单条连接用超大/负数 length 把累积缓冲撑爆。
     */
    public boolean isFrameLengthValid(int len) {
        return len >= 0 && len <= config.getMaxFrameLength();
    }

    /**
     * 认证门禁：未认证连接只允许 LOGIN 事件，其余一律拒绝。
     */
    public boolean isAuthorized(EventType eventType, LinkSession session) {
        if (eventType == EventType.LOGIN) {
            return true;
        }
        return session != null && session.isAuth();
    }

    private void scheduleAuthTimeout(ChannelHandlerContext ctx) {
        // 跑在该连接自己的 EventLoop 上，极轻
        ScheduledFuture<?> future = ctx.executor().schedule(() -> {
            LinkSession session = ctx.channel().attr(SESSION_KEY).get();
            if (session == null || !session.isAuth()) {
                log.warn("认证超时，关闭连接 {}", ctx.channel().id());
                ctx.close();
            }
        }, this.config.getAuthTimeoutSeconds(), TimeUnit.SECONDS);
        ctx.channel().attr(AUTH_TIMEOUT_KEY).set(future);
    }

    private void cancelAuthTimeout(Channel channel) {
        ScheduledFuture<?> future = channel.attr(AUTH_TIMEOUT_KEY).getAndSet(null);
        if (future != null) {
            future.cancel(false);
        }
    }

    private String ip(Channel channel) {
        SocketAddress addr = channel.remoteAddress();
        if (addr instanceof InetSocketAddress) {
            return ((InetSocketAddress) addr).getHostString();
        }
        return "unknown";
    }
}
