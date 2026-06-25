package com.link.im.service;

import com.link.core.config.LinkCoreConfig;
import com.link.common.core.event.EventType;
import com.link.core.session.service.LinkSession;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月21日
 */
@Slf4j
@Component
public class LinkMessageSender {

    @Autowired
    private LinkCoreConfig config;


    public void send(String userId,EventType eventType,Object obj){

        // 跨进程降级：本进程（如 api）没有该用户的在线 session 时，sessionManager 返回 null/空。
        // 此处不抛错、直接返回——在线推送本应由持有连接的 gateway 进程完成。
        // TODO 微服务化：改为查 Redis 在线表定位 gateway 节点，并经 MQ 投递推送指令。
        List<LinkSession> sessions = this.config.getSessionManager().getSession(userId);

        if (sessions == null || sessions.isEmpty()) {
            log.debug("用户 {} 在本进程无在线 session，跳过在线推送（待 Redis/MQ 接入）", userId);
            return;
        }
        List<Channel> channels = sessions.stream().map(LinkSession::getChannel).toList();
        this.config.getLinkSender().send(eventType,channels,obj);
    }


}
