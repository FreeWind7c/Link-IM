package com.link.dispatcher.handler;

import com.link.common.channel.DefaultChannelAttributeKeys;
import com.link.common.core.model.data.PackData;
import com.link.common.core.model.user.LinkUserAuthData;
import com.link.core.config.LinkCoreConfig;
import com.link.common.core.event.EventType;
import com.link.core.event.handler.EventHandler;
import com.link.core.session.service.LinkSession;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.link.dispatcher.handler.base.BasePlatformEventHandler;
import com.link.base.redis.BasePlatFormRedisService;
import com.link.common.util.TokenUtil;
import com.link.base.manager.CacheDataManager;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.AttributeKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月17日
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LinkLoginAuthEventHandler extends BasePlatformEventHandler implements EventHandler {

    private final LinkCoreConfig config;

    private final BasePlatFormRedisService redisService;

    private final CacheDataManager cacheManager;

    @Override
    public EventType event() {
        return EventType.LOGIN;
    }

    @Override
    public Class<?> bodyClass() {
        return LinkUserAuthData.class;
    }

    @Override
    public void handler(Object obj, Channel channel) {
        LinkUserAuthData linkData = (LinkUserAuthData) obj;
        if (linkData == null) {
            log.warn("用戶-> {}认证数据为空",channel.id());
            return;
        }

        if (linkData.getToken() == null || linkData.getToken().isEmpty()) {
            log.warn("连接 {} 认证失败：token 为空，关闭连接", channel.id());
            channel.close();
            return;
        }

        DecodedJWT jwt;
        try {
            jwt = TokenUtil.verify(linkData.getToken());
        } catch (JWTVerificationException e) {
            log.warn("连接 {} 认证失败：token 非法或已过期（{}），关闭连接", channel.id(), e.getMessage());
            channel.close();
            return;
        }

        // 身份字段一律取自验证通过的 token，忽略 linkData 里客户端自报的值
        String userId = jwt.getClaim("userId").asString();
        String platformClaim = jwt.getClaim("platform").asString();
        if (userId == null || platformClaim == null) {
            log.warn("连接 {} 认证失败：token 缺少 userId/platform 声明，关闭连接", channel.id());
            channel.close();
            return;
        }

        short platform = Short.parseShort(platformClaim);
        String currentToken = this.cacheManager.getUserToken(userId, platform);
        if (currentToken == null || !currentToken.equals(linkData.getToken())) {
            log.warn("连接 {} 认证失败：token 已不是 userId={} platform={} 的当前有效 token（账号已在别处重新登录），通知该端下线",
                    channel.id(), userId, platform);
            PackData packData = new PackData(EventType.USER_EXIT.getAction(), 0, new byte[0]);
            channel.writeAndFlush(packData).addListener(ChannelFutureListener.CLOSE);
            return;
        }

        LinkSession oldSession = this.config.getSessionManager().getSession(userId, platform);
        LinkSession session = (LinkSession) channel.attr(AttributeKey.valueOf(DefaultChannelAttributeKeys.SESSION)).get();

        if (session == null) {
            log.warn("连接 {} 缺少 session 属性，关闭连接", channel.id());
            channel.close();
            return;
        }

        session.setAuth(true);
        session.setSessionId(userId);
        session.setPlatform(platform);
        session.setLastHeartbeatTime(System.currentTimeMillis());
        this.config.getConnectionSecurityManager().onAuthSuccess(channel);

        if (oldSession != null && oldSession != session && oldSession.getChannel() != null
                && oldSession.getChannel().isActive())  {
            log.warn("用户 {} platform={} 顶号登录，通知旧连接 {} 下线", userId, platform, oldSession.getChannel().id());
            PackData packData = new PackData(EventType.USER_EXIT.getAction(), 0, new byte[0]);
            oldSession.getChannel().writeAndFlush(packData)
                    .addListener(ChannelFutureListener.CLOSE);
        }

        // 旧连接关闭触发的 channelInactive 会 removeSession(oldSession)，但那是「值相等才删」，
        // 删不掉这里刚放进去的新会话，两种先后顺序都安全。
        this.config.getSessionManager().addSession(session);

        printf("认证成功，用户信息:",linkData,LinkUserAuthData.class);
    }
}
