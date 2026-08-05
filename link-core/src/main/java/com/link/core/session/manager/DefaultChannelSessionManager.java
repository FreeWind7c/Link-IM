package com.link.core.session.manager;

import com.link.core.config.LinkCoreConfig;
import com.link.core.session.service.LinkSession;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月16日
 */
@Slf4j
public class DefaultChannelSessionManager implements LinkSessionManager {

    private final ConcurrentHashMap<String, ConcurrentHashMap<Integer, LinkSession>> sessionMap = new ConcurrentHashMap<>();

    @Override
    public List<Channel> getChannel(String userId) {
            List<LinkSession> session = this.getSession(userId);
            if (session == null)
                return null;
            List<Channel> channels = session.stream().map(LinkSession::getChannel)
                    .filter(v -> v != null && v.isActive())
                    .collect(Collectors.toList());
            return channels;
    }

    @Override
    public void addSession(LinkSession session) {

        LinkSession[] oldHolder = new LinkSession[1];
        this.sessionMap.compute(session.getSessionId(), (userId, inner) -> {
            if (inner == null) {
                inner = new ConcurrentHashMap<>();
            }
            oldHolder[0] = inner.put(session.getPlatform(), session);
            return inner;
        });

        // 同 userId+platform 已有旧连接：踢旧（close 放在 compute 之外，避免在锁内做 IO）
        LinkSession old = oldHolder[0];
        if (old != null && old != session && old.getChannel() != null) {
            log.info("用户 {} 的 platform={} 重复登录，踢掉旧连接 {}",
                    session.getSessionId(), session.getPlatform(), old.getChannel().id());
            old.getChannel().close();
        }
    }

    @Override
    public List<LinkSession> getSession(String sessionId) {

        ConcurrentHashMap<Integer, LinkSession> inner = this.sessionMap.get(sessionId);
        if (inner == null) {
            return null;
        }
        return new ArrayList<>(inner.values());
    }

    @Override
    public LinkSession getSession(String sessionId, int platform) {
        ConcurrentHashMap<Integer, LinkSession> inner = this.sessionMap.get(sessionId);
        if (inner == null) {
            return null;
        }
        return inner.get(platform);
    }

    @Override
    public List<LinkSession> getByUserId(String userId) {
        ConcurrentHashMap<Integer, LinkSession> inner = this.sessionMap.get(userId);
        if (inner == null || inner.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(inner.values());
    }

    @Override
    public void removeSession(LinkSession session) {
        if (session == null || session.getSessionId() == null) {
            return;
        }
        this.sessionMap.computeIfPresent(session.getSessionId(), (userId, inner) -> {
            inner.remove(session.getPlatform(), session);
            return inner.isEmpty() ? null : inner;
        });
    }
}
