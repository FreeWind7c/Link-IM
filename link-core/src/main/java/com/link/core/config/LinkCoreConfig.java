package com.link.core.config;

import com.link.common.serialize.LinkJsonSerializer;
import com.link.common.serialize.service.LinkSerializer;

import com.link.core.sender.DefaultPackDataMessageSender;
import com.link.core.sender.LinkMessageSender;
import com.link.core.security.ConnectionSecurityManager;
import com.link.core.session.facotry.DefaultChannelSessionFactory;
import com.link.core.session.facotry.LinkSessionFactory;
import com.link.core.session.manager.DefaultChannelSessionManager;
import com.link.core.session.manager.LinkSessionManager;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Component;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月10日
 */
@Data
@Component
public class LinkCoreConfig {

    public static int PROTO_FRAME_LENGTH = 8;

    private short magic = 0x59C3;

    private int port = 8899;

    private int groupPushLimit = 50;

    // ---- 承载协议 ----

    /** 对外协议：TCP 或 WEBSOCKET。底层编解码与业务 handler 完全复用，仅 pipeline 前端不同。 */
    private Protocol protocol = Protocol.WEBSOCKET;

    /** WebSocket 握手路径，仅 protocol=WEBSOCKET 时生效，如 ws://host:port/ws */
    private String websocketPath = "/ws";

    /** WebSocket 单帧最大字节数（HttpObjectAggregator 聚合上限） */
    private int websocketMaxFrameSize = 1024 * 1024;

    private int bossGroupThreadCore = 1;

    private int soBackLog = 10240;

    private int workerGroupThreadCore = Runtime.getRuntime().availableProcessors() * 2;


    private boolean soReuseAddr = true;

    private boolean tcpNoDelay = true;

    private int soSndBuf = 1024 * 1024;

    private int soRcvBuf = 1024 * 1024;

    private int readerIdleTime = 5;

    private int writerIdleTime = 7;

    private int allIdleTime = 10;

    private int corePoolSize = Runtime.getRuntime().availableProcessors() * 2;

    private int maxPoolSize = Runtime.getRuntime().availableProcessors() * 4;


    private int seqPartitionCount = Runtime.getRuntime().availableProcessors() * 2;

    private int queueCapacity = 500;

    private int keepAlive = 60;

    private String threadNamePrefix = "link-im-worker";

    private LinkSessionFactory sessionFactory = new DefaultChannelSessionFactory(this);

    /**
     * 默认裸 JSON 序列化器（无业务多态适配器）。引擎层不认识具体消息类型，
     * 多态反序列化（如 AbstractMessage 的子类还原）由 link-im 在启动时通过
     * setLinkSerializer 注入带适配器的实例覆盖（见 link-im 的序列化配置）。
     */
    private LinkSerializer linkSerializer = new LinkJsonSerializer();

    private LinkSessionManager sessionManager = new DefaultChannelSessionManager();

    private LinkMessageSender linkSender = new DefaultPackDataMessageSender(this);

    // ---- 连接安全 ----

    /** 连接建立后多少秒内必须完成登录认证，否则踢掉 */
    private int authTimeoutSeconds = 10;

    /** 单个 IP 允许的最大并发连接数 */
    private int maxConnPerIp = 100;

    /** 全局最大并发连接数 */
    private int maxConnections = 100000;

    /** 单帧 body 允许的最大字节数，防止超大 length 撑爆缓冲 */
    private int maxFrameLength = 1024 * 1024;

    private ConnectionSecurityManager connectionSecurityManager = new ConnectionSecurityManager(this);



}
