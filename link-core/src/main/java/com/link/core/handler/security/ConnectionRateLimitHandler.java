package com.link.core.handler.security;

import com.google.common.util.concurrent.RateLimiter;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * 连接速率限流器
 * 使用 Guava RateLimiter 令牌桶算法限制每秒建立的新连接数，防止雷击效应
 *
 * 使用场景：
 * - 防止服务重启后 10 万连接同时重连导致的雪崩
 * - 防止 DDoS 攻击导致的连接风暴
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月29日
 */
@Slf4j
@ChannelHandler.Sharable
public class ConnectionRateLimitHandler extends ChannelInboundHandlerAdapter {

    /**
     * 令牌桶限流器：每秒生成 N 个令牌
     * 例如：permitsPerSecond=1000 表示每秒最多接受 1000 个新连接
     */
    private final RateLimiter rateLimiter;

    /**
     * 是否阻塞等待令牌
     * - false: 立即拒绝（推荐），客户端自己重试
     * - true: 排队等待，可能导致连接堆积
     */
    private final boolean blockWhenLimited;

    /**
     * 最大等待时间（毫秒），仅在 blockWhenLimited=true 时有效
     */
    private final long maxWaitMillis;

    /**
     * 构造函数
     *
     * @param permitsPerSecond 每秒允许的连接数
     * @param blockWhenLimited 是否阻塞等待（true=排队，false=立即拒绝）
     * @param maxWaitMillis 最大等待时间（毫秒），仅在 blockWhenLimited=true 时有效
     */
    public ConnectionRateLimitHandler(double permitsPerSecond, boolean blockWhenLimited, long maxWaitMillis) {
        this.rateLimiter = RateLimiter.create(permitsPerSecond);
        this.blockWhenLimited = blockWhenLimited;
        this.maxWaitMillis = maxWaitMillis;

        log.info("✅ 连接限流器已启用：每秒 {} 个连接，阻塞模式={}，最大等待={}ms",
            permitsPerSecond, blockWhenLimited, maxWaitMillis);
    }

    /**
     * 简化构造函数：默认不阻塞，立即拒绝
     *
     * @param permitsPerSecond 每秒允许的连接数
     */
    public ConnectionRateLimitHandler(double permitsPerSecond) {
        this(permitsPerSecond, false, 0);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        boolean acquired;
        if (blockWhenLimited) {
            // 阻塞模式：尝试获取令牌，最多等待 maxWaitMillis 毫秒
            acquired = rateLimiter.tryAcquire(10, maxWaitMillis, TimeUnit.MILLISECONDS);

            if (!acquired) {
                log.warn("🚫 连接限流触发（排队超时），拒绝连接: {}", ctx.channel().remoteAddress());
                ctx.close();
                return;
            }

            log.debug("✅ 连接通过限流检查（排队获取），远程地址: {}", ctx.channel().remoteAddress());

        }
        else {
            // 非阻塞模式：立即获取令牌，没有则拒绝
            acquired = rateLimiter.tryAcquire();
            if (!acquired) {
                log.warn("🚫 连接限流触发（立即拒绝），拒绝连接: {}", ctx.channel().remoteAddress());
                ctx.close();
                return;
            }
            log.info("✅ 连接通过限流检查（立即获取），远程地址: {}", ctx.channel().remoteAddress());
        }

        // 获取令牌成功，继续处理
        super.channelActive(ctx);
    }

    /**
     * 动态调整限流速率
     * 可用于根据服务器负载动态调整（例如：监控 CPU 使用率自动调整）
     *
     * @param permitsPerSecond 新的每秒连接数限制
     */
    public void updateRate(double permitsPerSecond) {
        rateLimiter.setRate(permitsPerSecond);
        log.info("🔧 连接限流速率已调整为：每秒 {} 个连接", permitsPerSecond);
    }

    /**
     * 获取当前限流速率
     *
     * @return 每秒允许的连接数
     */
    public double getRate() {
        return rateLimiter.getRate();
    }
}
