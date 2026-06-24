package com.link.im.handler;

import com.link.common.channel.DefaultChannelAttributeKeys;
import com.link.core.config.LinkCoreConfig;
import com.link.core.event.EventType;
import com.link.core.event.handler.EventHandler;
import com.link.core.model.user.LinkUserAuthData;
import com.link.core.session.manager.LinkSessionManager;
import com.link.core.session.service.LinkSession;
import com.link.util.print.LinkPrintJsonUtil;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPattern;

import java.net.InetSocketAddress;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月17日
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LinkLoginAuthEventHandler implements EventHandler {

    private final LinkCoreConfig config;


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
        if (linkData == null)
        {
            log.warn("用戶-> {}认证数据为空",channel.id());
            return;
        }
        LinkSession session = (LinkSession) channel.attr(AttributeKey.valueOf(DefaultChannelAttributeKeys.SESSION)).get();
        session.setAuth(true);
        session.setSessionId(linkData.getUserId());
        session.setPlatform(linkData.getPlatform());
        session.setLastHeartbeatTime(System.currentTimeMillis());
        // 认证通过：取消认证超时任务，连接转为长期持有
        this.config.getConnectionSecurityManager().onAuthSuccess(channel);
        this.config.getSessionManager().addSession(session);
        LinkPrintJsonUtil.print("认证成功，用户信息:",linkData,LinkUserAuthData.class);
    }
}
