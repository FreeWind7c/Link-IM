package com.link.core.session.manager;

import com.link.core.config.LinkCoreConfig;
import com.link.core.session.service.LinkSession;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多端在线表：userId -> (platform -> session)。
 * 同一 userId 可同时挂多个 platform（手机/桌面/平板并存），
 * 同一 userId+platform 互斥（同端重复登录踢旧）。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月16日
 */
@Slf4j
public class DefaultChannelSessionManager implements LinkSessionManager {

    private final ConcurrentHashMap<String, ConcurrentHashMap<Integer, LinkSession>> sessionMap = new ConcurrentHashMap<>();

    @Override
    public void addSession(LinkSession session) {
        // 【异地登录策略：踢旧】同 userId+platform 互斥——后登录的同端设备顶掉先登录的。
        // 例：账号在 Windows1 已在线，再用 Windows2（同为 windows 端）登录，则挤掉 Windows1。
        // 这是全系统唯一的异地登录处置点：只有连接层同时握有「真相」(本地在线表) 和「手段」(能 close 旧连接)；
        // HTTP 登录无状态、不持连接，不做该判断。
        // 注意：当前仅能踢「本节点」的旧连接；跨 Netty 节点的同端互踢需配合 Redis 在线表 + 踢人指令（待接入）。
        //
        // 整个「取/建内层 map + put」放进 compute，保证对同一 userId 原子，
        // 不会与并发的 removeSession(computeIfPresent) 互相错过而丢 session。
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
        // 原子地：只删「仍等于自己」的那条（双参 remove），删完该用户无在线端则移除外层条目。
        this.sessionMap.computeIfPresent(session.getSessionId(), (userId, inner) -> {
            inner.remove(session.getPlatform(), session);
            return inner.isEmpty() ? null : inner;
        });
    }
}
