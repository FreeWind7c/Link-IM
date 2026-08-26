package com.link.core.handler.idle;

import com.link.common.channel.DefaultChannelAttributeKeys;
import com.link.core.config.LinkCoreConfig;
import com.link.core.session.service.LinkSession;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月15日
 */
@Slf4j
@RequiredArgsConstructor
public class IdleHandler extends ChannelInboundHandlerAdapter {

    private final LinkCoreConfig config;

    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            switch (((IdleStateEvent) evt).state()) {
                // 客户端掉线 = 服务端读不到心跳 = READER_IDLE，下线判断挂在这里
                case READER_IDLE -> {
                    Channel channel = ctx.channel();
                    LinkSession session = (LinkSession) channel.attr(AttributeKey.valueOf(DefaultChannelAttributeKeys.SESSION)).get();
                    if (session == null) {
                        // 未注册/已清理的连接，直接关
                        channel.close();
                        return;
                    }
                    // 容忍丢 3 次心跳：累计静默超过 readerIdleTime*3 才判定下线
                    if (System.currentTimeMillis() - session.getLastHeartbeatTime() > this.config.getReaderIdleTime() * 3L * 1000) {
                        idleTimeout(channel, session);
                    }
                }
                case WRITER_IDLE -> log.debug("write_idle {}", ctx.channel().id());
                case ALL_IDLE -> log.debug("all_idle {}", ctx.channel().id());
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    private void idleTimeout(Channel channel, LinkSession session) {
        log.warn("用户-> {},platform={} 心跳超时下线!", session.getSessionId(), session.getPlatform());
        // 先从在线表摘除，再关连接（关连接触发的 channelInactive 还会再 removeSession 一次，幂等无副作用）
        this.config.getSessionManager().removeSession(session);
        channel.close();
    }
}
