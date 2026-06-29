package com.link.core.session;

import com.link.core.config.LinkCoreConfig;
import com.link.core.session.service.LinkSession;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月12日
 */
public abstract class AbstractSession<C extends LinkCoreConfig> implements LinkSession {
    protected C config;
    protected String sessionId;
    protected String host;
    protected int port;
    protected boolean auth;

    protected int platform;

    // 最后一次心跳时间
    protected long lastHeartTime;

}
