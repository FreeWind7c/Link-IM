package com.link.register.client.demo;

import com.link.register.client.RegistryClient;

/**
 * 心跳 + 订阅 端到端 Demo —— 在同一个 JVM 里起两个客户端，完整演示「掉线被感知」的闭环：
 *
 * <ol>
 *   <li><b>consumer</b>：订阅 link-restapi，收到列表变更就打印（它就是「关心 link-restapi 的其他服务」）。</li>
 *   <li><b>provider</b>：注册一个 link-restapi 实例并持续发心跳。</li>
 *   <li>过几秒后 provider <b>停发心跳</b>（模拟进程假死/被 kill）。</li>
 *   <li>服务端 HealthChecker 超时（默认 15s）检测到心跳断开 → 打印「service/ip/port」下线日志 → 推送最新列表给订阅者。</li>
 *   <li>consumer 收到新快照，diff 出消失的实例，打印 <b>🔴【下线】service/ip/port</b>。</li>
 * </ol>
 *
 * <p>前提：link-register-server 已启动并监听 9500。直接右键 Run 本 main。
 * 建议把服务端 REGISTER_HEALTH_TIMEOUT_MS 调小（如 6000）以更快看到下线效果。
 */
public class HeartbeatSubscribeDemo {

    public static void main(String[] args) throws Exception {
        String host = "localhost";
        int port = 9500;

        // 1) 订阅方：关心 link-restapi 的「其他服务」
        RegistryClient consumer = new RegistryClient(host, port);
        consumer.subscribe("link-restapi");

        // 2) 提供方：注册 link-restapi 一个实例，并开始心跳（每 3s 一次）
        RegistryClient provider = new RegistryClient(host, port);
        provider.register("link-restapi", "192.168.1.100", 8080);
        provider.startHeartbeat(3000);

        // 3) 让它正常心跳 8 秒（订阅方应先看到🟢上线）
        Thread.sleep(8000);

        // 4) 模拟提供方「假死」：停发心跳，但不主动注销
        System.out.println("\n=== 模拟 provider 掉线：停止心跳 ===\n");
        provider.stopHeartbeat();

        // 5) 等待超过服务端心跳超时阈值，健康检查会踢除并推送 → 订阅方打印🔴下线
        Thread.sleep(20000);

        System.out.println("\n=== Demo 结束 ===");
        provider.shutdown();
        consumer.shutdown();
    }
}
