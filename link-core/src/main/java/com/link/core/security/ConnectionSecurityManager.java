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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 连接安全管理器
 *
 * 功能：
 * 1. 全局连接数限制
 * 2. 单IP连接数限制
 * 3. IP黑名单（永久封禁）
 * 4. IP自动封禁（恶意行为检测）
 * 5. 认证超时检查
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

    /** IP 黑名单（永久封禁） */
    private final ConcurrentHashMap<String, Long> ipBlacklist = new ConcurrentHashMap<>();

    /** IP 短时连接统计（用于检测恶意行为） */
    private final ConcurrentHashMap<String, IpConnStats> ipConnStats = new ConcurrentHashMap<>();

    /** 标记该连接是否已计入计数，保证 onDisconnect 只扣减一次 */
    private static final AttributeKey<Boolean> COUNTED_KEY = AttributeKey.valueOf("CONN_COUNTED");

    /** 认证超时任务句柄，登录成功后用它取消 */
    private static final AttributeKey<ScheduledFuture<?>> AUTH_TIMEOUT_KEY = AttributeKey.valueOf("AUTH_TIMEOUT_FUTURE");

    private static final AttributeKey<LinkSession> SESSION_KEY = AttributeKey.valueOf(DefaultChannelAttributeKeys.SESSION);

    /** 恶意行为检测：5秒内连接超过N次视为攻击 */
    private static final int MALICIOUS_WINDOW_SECONDS = 5;
    private static final int MALICIOUS_CONN_THRESHOLD = 50;  // 5秒内超过50个连接

    /** 自动封禁时长（毫秒） */
    private static final long AUTO_BAN_DURATION_MS = 10 * 60 * 1000;  // 10分钟

    public ConnectionSecurityManager(LinkCoreConfig config) {
        this.config = config;

        // 定期清理过期的统计数据和封禁记录
        startCleanupTask();
    }

    /**
     * 连接建立时调用：黑名单检查 + 恶意行为检测 + 连接数校验 + 认证超时
     */
    public boolean onConnect(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();
        String ip = ip(channel);

        // 🔑 1. 检查 IP 黑名单
        if (isBlacklisted(ip)) {
            log.warn("🚫 IP 在黑名单中，拒绝连接：{}", ip);
            channel.close();
            return false;
        }

        // 🔑 2. 恶意行为检测（自动封禁）
        if (isMaliciousBehavior(ip)) {
            log.warn("🚫 检测到恶意行为，自动封禁 IP：{} ({}分钟)", ip, AUTO_BAN_DURATION_MS / 60000);
            addToBlacklist(ip, AUTO_BAN_DURATION_MS);
            channel.close();
            return false;
        }

        // 🔑 3. 先检查，再计数（避免计数泄漏）
        int currentTotal = totalConn.get();
        int currentPerIp = ipConnCount.getOrDefault(ip, 0);

        // 全局连接数检查
        if (currentTotal >= config.getMaxConnections()) {
            log.warn("全局连接数超限({}>={}，拒绝连接 {}", currentTotal, config.getMaxConnections(), channel.id());
            channel.close();
            return false;
        }

        // 单IP连接数检查
        if (currentPerIp >= config.getMaxConnPerIp()) {
            log.warn("IP={} 连接数超限({}>={}，拒绝连接 {}", ip, currentPerIp, config.getMaxConnPerIp(), channel.id());
            channel.close();
            return false;
        }

        // 🔑 4. 检查通过，才进行计数
        int total = totalConn.incrementAndGet();
        int perIp = ipConnCount.merge(ip, 1, Integer::sum);

        // 标记已计数；关连接后 channelInactive -> onDisconnect 会据此扣减
        channel.attr(COUNTED_KEY).set(Boolean.TRUE);

        // 5. 安排认证超时检查
        scheduleAuthTimeout(ctx);

        log.debug("✅ 连接通过安全检查：IP={}, 当前{}连接, 该IP{}连接", ip, total, perIp);
        return true;
    }

    /**
     * 连接断开时调用：扣减计数 + 取消认证超时任务
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
     * 认证成功后调用：取消认证超时任务，连接从「待认证」转为长期持有
     */
    public void onAuthSuccess(Channel channel) {
        cancelAuthTimeout(channel);
    }

    /**
     * 帧长度校验：防止单条连接用超大/负数 length 把累积缓冲撑爆
     */
    public boolean isFrameLengthValid(int len) {
        return len >= 0 && len <= config.getMaxFrameLength();
    }

    /**
     * 认证门禁：未认证连接只允许 LOGIN 事件，其余一律拒绝
     */
    public boolean isAuthorized(EventType eventType, LinkSession session) {
        if (eventType == EventType.LOGIN) {
            return true;
        }
        return session != null && session.isAuth();
    }

    /**
     * 检查 IP 是否在黑名单中
     */
    private boolean isBlacklisted(String ip) {
        Long expireTime = ipBlacklist.get(ip);
        if (expireTime == null) {
            return false;
        }

        // 检查是否过期
        if (expireTime > 0 && System.currentTimeMillis() > expireTime) {
            ipBlacklist.remove(ip);
            return false;
        }

        return true;
    }

    /**
     * 将 IP 加入黑名单
     *
     * @param ip IP 地址
     * @param durationMs 封禁时长（毫秒），0 表示永久封禁
     */
    public void addToBlacklist(String ip, long durationMs) {
        long expireTime = (durationMs > 0) ? System.currentTimeMillis() + durationMs : 0;
        ipBlacklist.put(ip, expireTime);

        if (durationMs > 0) {
            log.warn("🚫 IP {} 已加入黑名单，封禁 {} 分钟", ip, durationMs / 60000);
        } else {
            log.warn("🚫 IP {} 已永久封禁", ip);
        }
    }

    /**
     * 从黑名单中移除 IP
     */
    public void removeFromBlacklist(String ip) {
        if (ipBlacklist.remove(ip) != null) {
            log.info("✅ IP {} 已从黑名单移除", ip);
        }
    }

    /**
     * 恶意行为检测：短时间内大量连接
     */
    private boolean isMaliciousBehavior(String ip) {
        long now = System.currentTimeMillis();

        IpConnStats stats = ipConnStats.compute(ip, (k, v) -> {
            if (v == null) {
                v = new IpConnStats();
            }
            v.addConnection(now);
            return v;
        });

        int recentConnCount = stats.getRecentConnectionCount(now, MALICIOUS_WINDOW_SECONDS * 1000);

        // 5秒内连接数超过阈值，视为恶意
        return recentConnCount > MALICIOUS_CONN_THRESHOLD;
    }

    /**
     * 定期清理过期的统计数据和封禁记录
     */
    private void startCleanupTask() {
        // 每分钟清理一次
        Thread cleanupThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(60_000);

                    long now = System.currentTimeMillis();

                    // 清理过期的黑名单
                    ipBlacklist.entrySet().removeIf(entry -> {
                        long expireTime = entry.getValue();
                        return expireTime > 0 && now > expireTime;
                    });

                    // 清理过期的统计数据
                    ipConnStats.entrySet().removeIf(entry -> {
                        return entry.getValue().isExpired(now, 60_000);
                    });

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("清理任务异常", e);
                }
            }
        }, "ConnectionSecurityCleanup");

        cleanupThread.setDaemon(true);
        cleanupThread.start();
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

    /**
     * 获取当前连接统计信息
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("totalConnections", totalConn.get());
        stats.put("blacklistedIPs", ipBlacklist.size());
        stats.put("trackedIPs", ipConnStats.size());
        stats.put("ipConnections", new ConcurrentHashMap<>(ipConnCount));
        return stats;
    }

    /**
     * IP 连接统计（用于恶意行为检测）
     */
    private static class IpConnStats {
        private final ConcurrentHashMap<Long, AtomicInteger> timeWindowCount = new ConcurrentHashMap<>();

        void addConnection(long timestamp) {
            // 按秒分桶
            long bucket = timestamp / 1000;
            timeWindowCount.computeIfAbsent(bucket, k -> new AtomicInteger()).incrementAndGet();
        }

        int getRecentConnectionCount(long now, long windowMs) {
            long startBucket = (now - windowMs) / 1000;
            return timeWindowCount.entrySet().stream()
                .filter(e -> e.getKey() >= startBucket)
                .mapToInt(e -> e.getValue().get())
                .sum();
        }

        boolean isExpired(long now, long expireMs) {
            if (timeWindowCount.isEmpty()) {
                return true;
            }
            long oldestBucket = timeWindowCount.keySet().stream().min(Long::compareTo).orElse(now / 1000);
            return (now - oldestBucket * 1000) > expireMs;
        }
    }
}
