package com.link.core.session;

import com.link.core.config.LinkCoreConfig;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月12日
 */
public class DefaultChannelSession extends AbstractSession<LinkCoreConfig> {


    private Channel channel;

    public DefaultChannelSession( Channel channel) {
        this.channel = channel;
    }

    @Override
    public boolean isAuth() {
        return this.auth;
    }

    @Override
    public void setAuth(boolean auth) {
        this.auth = auth;
    }

    @Override
    public void addAttribute(String key, Object value) {
        this.channel.attr(AttributeKey.valueOf(key)).set(value);
    }

    @Override
    public void setLastHeartbeatTime(long time) {
        this.lastHeartTime = time;
    }

    @Override
    public long getLastHeartbeatTime() {
        return this.lastHeartTime;
    }

    @Override
    public Object getAttribute(String key) {
        return this.channel.attr(AttributeKey.valueOf(key)).get();
    }

    @Override
    public Object removeAttribute(String key) {
        return this.channel.attr(AttributeKey.valueOf(key)).getAndSet(null);
    }

    @Override
    public <T> T getAttribute(String key, Class<T> t) {
        return t.cast(this.channel.attr(AttributeKey.valueOf(key)).get());
    }

    @Override
    public String getHost() {
        return this.host;
    }

    @Override
    public void setHost(String host) {
        this.host = host;
    }

    @Override
    public int getPort() {
        return this.port;
    }

    @Override
    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public String getSessionId() {
        return this.sessionId;
    }

    @Override
    public Channel getChannel() {
        return this.channel;
    }

    @Override
    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    @Override
    public void setPlatform(short platform) {
        this.platform = platform;
    }

    @Override
    public int getPlatform() {
        return this.platform;
    }

    @Override
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

}
