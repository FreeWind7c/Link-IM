package com.link.im.handler;

import com.link.common.channel.DefaultChannelAttributeKeys;
import com.link.common.core.model.data.PackData;
import com.link.common.core.model.user.LinkUserAuthData;
import com.link.core.config.LinkCoreConfig;
import com.link.common.core.event.EventType;
import com.link.core.event.handler.EventHandler;
import com.link.core.session.manager.LinkSessionManager;
import com.link.core.session.service.LinkSession;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.link.im.handler.base.BasePlatformEventHandler;
import com.link.im.service.LinkRedisService;
import com.link.common.util.TokenUtil;
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

    private final LinkRedisService redisService;


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

        // JWT 校验只能证明「这串 token 是我们签发的、没被篡改」，证明不了「它还是当前有效的那一个」。
        // 典型场景：A 端断网期间 B 端用同一账号重新登录，A 手里的旧 token 签名依然合法；
        // 若直接放行，A 恢复网络后一发 LOGIN 就会走下面的顶号逻辑，反过来把刚登录的 B 踢掉。
        // 因此再和 Redis 里该 userId+platform 的当前 token 比对一次（登录时由 UserInfoService.userAuth 写入）。
        String currentToken = this.redisService.getUserToken(userId, platform);
        if (currentToken == null || !currentToken.equals(linkData.getToken())) {
            log.warn("连接 {} 认证失败：token 已不是 userId={} platform={} 的当前有效 token（账号已在别处重新登录），通知该端下线",
                    channel.id(), userId, platform);
            // 给这条**新连接**回一帧 USER_EXIT 再关闭：前端收到会清空本地数据并回登录页，
            // 而不是傻乎乎地退避重连、反复拿废 token 来撞。注意不要动已有的在线会话。
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

        // 踢旧端必须在 addSession 之前：addSession 内部对同 userId+platform 的旧连接会直接
        // close()，排在它后面发 USER_EXIT 就晚了——两条连接不在同一 eventLoop 时 close 任务先
        // 执行，写操作落到已关闭的 channel 上；在同一 eventLoop 时 close 更是同步生效，
        // 连 isActive() 都过不了。所以先通知旧端、再登记新会话。
        if (oldSession != null && oldSession != session && oldSession.getChannel() != null
                && oldSession.getChannel().isActive())  {
            log.warn("用户 {} platform={} 顶号登录，通知旧连接 {} 下线", userId, platform, oldSession.getChannel().id());
            // 直接写 PackData：出站由 TcpOutboundHandler(MessageToByteEncoder<PackData>) 编码成
            // ByteBuf，再由 WebSocketFrameAdapter 包成 BinaryWebSocketFrame。
            // 若先 serialize 成 byte[] 再写，这两个 handler 都不认这个类型，会一路透传到
            // WebSocket/Http 编码器后抛 UnsupportedMessageTypeException——promise 没人监听，
            // 于是「后端有日志、前端收不到」。
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
