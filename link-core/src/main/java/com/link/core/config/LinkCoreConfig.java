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

    private boolean partitionConsumption = true;

    /**
     * 单个会话允许的在飞重活数上限。
     *
     * <p>共享线程池是一条全用户公用的 FIFO 队列，没有这个限制时压测会话能把队列灌满，
     * 后到的其他用户被连带丢弃或拖慢。这个值让洪水会话先撞上限，队列给其他会话留出余量。
     * 调小 = 隔离性更强、单会话峰值吞吐更低；调大 = 反之。
     */
    private int maxInflightPerChat = 16;

    /**
     * 单个分区的排队上限。
     *
     * <p>原先分区用无界队列，压测多少消息就堆多少，没有任何反压回到客户端——
     * 内存一路涨，且积压会全量转成对下游（Mongo/MQ）的压力。有界之后超出的直接拒，
     * 客户端靠 ACK 超时重发，压力挡在入口而不是传导到下游。
     */
    private int partitionQueueCapacity = 2000;

    private int keepAlive = 60;

    private String threadNamePrefix = "link-im-worker";

    private LinkSessionFactory sessionFactory = new DefaultChannelSessionFactory(this);

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

    private int maxFrameLength = 2 * 1024 * 1024;

    private ConnectionSecurityManager connectionSecurityManager = new ConnectionSecurityManager(this);

    // ---- 连接限流配置 ----

    /** 是否启用连接限流 */
    private boolean connectionRateLimitEnabled = true;

    /** 每秒允许的新连接数（0表示不限制） */
    private double connectionRateLimitPerSecond = 1;

    /** 连接限流是否阻塞等待（false=立即拒绝，true=排队等待） */
    private boolean connectionRateLimitBlock = false;

    /** 连接限流最大等待时间（毫秒），仅在 connectionRateLimitBlock=true 时有效 */
    private long connectionRateLimitMaxWait = 1000;





}
