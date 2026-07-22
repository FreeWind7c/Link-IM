package com.link.register.server.core;

import com.link.register.grpc.Instance;
import com.link.register.server.core.manager.ServerInstanceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 健康检查 —— 定时扫描，踢除心跳超时的实例。
 *
 * <p>这就是让 heartbeat「有意义」的那一半：客户端发心跳刷新时间，本类定时检查，
 * 谁超过 timeout 没心跳就摘除。二者缺一不可——只发心跳没人检查等于没做。
 *
 * <p>经验值：超时阈值 ≈ 客户端心跳间隔的 3 倍（心跳 5s → 超时 15s），容忍偶发丢包。
 */
@Component
public class HealthChecker {

    private static final Logger log = LoggerFactory.getLogger(HealthChecker.class);

    private final ServerInstanceManager manager;

    @Value("${link.registry.health.timeout-ms:15000}")
    private long timeoutMs;

    public HealthChecker(ServerInstanceManager manager) {
        this.manager = manager;
    }

    /** 定时扫描，踢除心跳早于 (now - timeout) 的实例 */
    @Scheduled(fixedDelayString = "${link.registry.health.scan-interval-ms:5000}")
    public void scan() {
        long deadline = System.currentTimeMillis() - timeoutMs;
        List<Instance> evicted = manager.evictExpired(deadline);
        if (evicted.isEmpty()) {
            return;
        }
        // 逐个打印「断开的服务名 / ip / 端口」，一眼看清是谁掉线了；
        // evictExpired 内部已把最新实例列表推给了各自的订阅者（其他服务）。
        for (Instance inst : evicted) {
            log.warn("❌ 心跳断开，服务下线 → service={}, ip={}, port={}（已推送订阅者）",
                    inst.getServiceName(), inst.getIp(), inst.getPort());
        }
        log.info("健康检查本轮踢除 {} 个超时实例", evicted.size());
    }
}
