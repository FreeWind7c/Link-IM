package com.link.core.session.service;

import io.netty.channel.Channel;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月12日
 */
public interface LinkSession {

    boolean isAuth();

    void setAuth(boolean auth);

    void addAttribute(String key, Object value);

    void setLastHeartbeatTime(long time);

    long getLastHeartbeatTime();

    Object getAttribute(String key);

    Object removeAttribute(String key);

    <T> T getAttribute(String key, Class<T> t);

    String getHost();

    void setHost(String host);

    int getPort();

    void setPort(int port);


    Channel getChannel();

    void setChannel(Channel channel);

    void setPlatform(short platform);
    int getPlatform();

    void setSessionId(String sessionId);

    String getSessionId();

}
