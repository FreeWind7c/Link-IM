package com.link.core.session.manager;

import com.link.core.session.service.LinkSession;
import io.netty.channel.Channel;

import java.util.List;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月16日
 */
public interface LinkSessionManager {

    /** 认证成功后登记会话（同 userId+platform 已存在则踢旧） */
    void addSession(LinkSession session);

    /** 取某用户某一端的会话 */
    LinkSession getSession(String userId, int platform);

    List<LinkSession> getSession(String userId);

    /** 取某用户全部在线端（多端群发用），无在线端返回空列表 */
    List<LinkSession> getByUserId(String userId);

    /** 断开/下线时移除会话；只移除「仍等于自己」的那条，避免误删重连产生的新会话 */
    void removeSession(LinkSession session);

    List<Channel> getChannel(String userId);

    /** 获取所有在线会话（用于优雅停服时广播消息） */
    List<LinkSession> getAllSessions();
}
