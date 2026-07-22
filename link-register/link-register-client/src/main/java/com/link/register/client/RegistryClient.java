package com.link.register.client;

import com.link.register.grpc.*;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 注册中心客户端 SDK —— 把「注册 / 心跳 / 订阅」三件事封装成好用的一个对象。
 *
 * <p>相比 {@code SimpleClientDemo}（手动挡、一次性），本类是「自动挡」：
 * <ul>
 *   <li><b>心跳</b>：{@link #startHeartbeat} 起一个后台定时线程，按固定间隔给注册中心发 heartbeat，
 *       刷新服务端 lastHeartbeat。停发心跳（进程假死/被杀）后，服务端 HealthChecker 超时就会把本实例踢下线。</li>
 *   <li><b>订阅</b>：{@link #subscribe} 走 gRPC server-stream 异步流，服务端实例列表一变就主动下推。
 *       本类缓存上一次快照，收到新快照后做 diff，谁没了就当「下线」打印出来（这就是「收到断开要打印」的落点）。</li>
 * </ul>
 *
 * <p>心跳和订阅互不阻塞：心跳跑在自带的 ScheduledExecutor 上，订阅用异步 stub 的回调线程，
 * 主线程调用完 {@code startHeartbeat()/subscribe()} 立即返回。
 */
public class RegistryClient {

    private static final Logger log = LoggerFactory.getLogger(RegistryClient.class);

    private final ManagedChannel channel;
    private final RegistryServiceGrpc.RegistryServiceBlockingStub blockingStub;
    private final RegistryServiceGrpc.RegistryServiceStub asyncStub;

    /** 心跳定时线程池；每个订阅服务一份「上次快照」缓存，用来 diff 出上/下线 */
    private final ScheduledExecutorService heartbeatPool =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "registry-heartbeat");
                t.setDaemon(true);
                return t;
            });
    private final Map<String, Map<String, Instance>> lastSnapshots = new ConcurrentHashMap<>();

    /** 本客户端自己注册的实例身份（心跳要用），register() 后填充 */
    private volatile String selfService;
    private volatile String selfIp;
    private volatile int selfPort;

    public RegistryClient(String host, int port) {
        this.channel = NettyChannelBuilder.forAddress(host, port)
                .usePlaintext()
                // 连接层保活：30s 无数据发 PING，10s 不回就断连；即使当前没有活跃调用也保活
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(10, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                .build();
        this.blockingStub = RegistryServiceGrpc.newBlockingStub(channel);
        this.asyncStub = RegistryServiceGrpc.newStub(channel);
    }

    // ==================== 注册 / 注销 ====================

    /** 注册自身实例。成功后记住身份，供后续心跳/注销复用。 */
    public boolean register(String serviceName, String ip, int port) {
        Instance instance = Instance.newBuilder()
                .setServiceName(serviceName)
                .setIp(ip)
                .setPort(port)
                .setWeight(1.0)
                .setEphemeral(true)     // 临时实例：连接断/心跳超时即摘除
                .build();
        CommonResponse resp = blockingStub.register(
                RegisterRequest.newBuilder().setInstance(instance).build());
        if (resp.getSuccess()) {
            this.selfService = serviceName;
            this.selfIp = ip;
            this.selfPort = port;
            log.info("✅ 注册成功: service={}, {}:{}", serviceName, ip, port);
        } else {
            log.warn("注册失败: {}", resp.getMessage());
        }
        return resp.getSuccess();
    }

    /** 优雅注销自身实例（主动下线，不用等心跳超时）。 */
    public void deregister() {
        if (selfService == null) {
            return;
        }
        blockingStub.deregister(DeregisterRequest.newBuilder()
                .setServiceName(selfService).setIp(selfIp).setPort(selfPort).build());
        log.info("已注销: service={}, {}:{}", selfService, selfIp, selfPort);
    }

    // ==================== 心跳 ====================

    /**
     * 启动应用层心跳：按 intervalMs 周期给注册中心发 heartbeat，刷新本实例的存活时间。
     * 必须先 {@link #register} 成功。停发心跳后，服务端超时会把本实例判为「断开」并下线。
     */
    public void startHeartbeat(long intervalMs) {
        if (selfService == null) {
            throw new IllegalStateException("请先 register() 再 startHeartbeat()");
        }
        HeartbeatRequest req = HeartbeatRequest.newBuilder()
                .setServiceName(selfService).setIp(selfIp).setPort(selfPort).build();
        heartbeatPool.scheduleAtFixedRate(() -> {
            try {
                CommonResponse resp = blockingStub.heartbeat(req);
                if (resp.getSuccess()) {
                    log.debug("💓 心跳ok: {} {}:{}", selfService, selfIp, selfPort);
                } else {
                    // 服务端说 not found —— 可能已被踢，尝试重新注册兜底
                    log.warn("心跳被拒（{}），尝试重新注册", resp.getMessage());
                    register(selfService, selfIp, selfPort);
                }
            } catch (Exception e) {
                log.warn("心跳发送失败: {}", e.toString());
            }
        }, 0, intervalMs, TimeUnit.MILLISECONDS);
        log.info("💓 心跳已启动，间隔 {}ms", intervalMs);
    }

    /** 停止发送心跳（用于模拟「进程假死/被杀」——之后服务端会超时踢除本实例）。 */
    public void stopHeartbeat() {
        heartbeatPool.shutdownNow();
        log.info("💤 心跳已停止: {} {}:{}", selfService, selfIp, selfPort);
    }

    // ==================== 订阅（推送 + 下线打印）====================

    /**
     * 订阅某个服务的实例列表变更。服务端会先推一份当前快照，之后每次变更（上线/下线）再推最新快照。
     * 本方法对相邻两次快照做 diff：新增=上线，消失=下线，并把下线实例的 name/ip/port 打印出来。
     */
    public void subscribe(String serviceName) {
        lastSnapshots.put(serviceName, new ConcurrentHashMap<>());
        asyncStub.subscribe(
                SubscribeRequest.newBuilder().setServiceName(serviceName).build(),
                new StreamObserver<>() {
                    @Override
                    public void onNext(InstanceList snapshot) {
                        onSnapshot(serviceName, snapshot);
                    }

                    @Override
                    public void onError(Throwable t) {
                        log.warn("订阅流出错: service={}, err={}", serviceName, t.toString());
                    }

                    @Override
                    public void onCompleted() {
                        log.info("订阅流结束: service={}", serviceName);
                    }
                });
        log.info("📡 已订阅: service={}", serviceName);
    }

    /** 收到一份新快照：与上次缓存 diff，打印上线/下线，然后用新快照替换缓存。 */
    private void onSnapshot(String serviceName, InstanceList snapshot) {
        Map<String, Instance> current = new LinkedHashMap<>();
        for (Instance inst : snapshot.getInstancesList()) {
            current.put(inst.getIp() + ":" + inst.getPort(), inst);
        }
        Map<String, Instance> previous = lastSnapshots.getOrDefault(serviceName, Map.of());

        // 上线：这次有、上次没有
        for (Map.Entry<String, Instance> e : current.entrySet()) {
            if (!previous.containsKey(e.getKey())) {
                Instance i = e.getValue();
                log.info("🟢【上线】service={}, ip={}, port={}", i.getServiceName(), i.getIp(), i.getPort());
            }
        }
        // 下线：上次有、这次没有 —— 这就是「收到断开事件要打印出来」的地方
        for (Map.Entry<String, Instance> e : previous.entrySet()) {
            if (!current.containsKey(e.getKey())) {
                Instance i = e.getValue();
                log.warn("🔴【下线】service={}, ip={}, port={}", i.getServiceName(), i.getIp(), i.getPort());
            }
        }
        lastSnapshots.put(serviceName, current);
        log.info("📮 收到 {} 最新实例列表，共 {} 个（version={}）",
                serviceName, snapshot.getInstancesCount(), snapshot.getLastModified());
    }

    // ==================== 关闭 ====================

    public void shutdown() {
        heartbeatPool.shutdownNow();
        channel.shutdown();
        try {
            channel.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
