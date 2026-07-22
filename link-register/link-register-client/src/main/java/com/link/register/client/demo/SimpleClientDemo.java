package com.link.register.client.demo;

import com.link.register.grpc.*;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.ManagedChannel;

import java.util.Iterator;
import java.util.List;

/**
 * 最简单的 gRPC 客户端 Demo —— 一个 main 方法，跑起来就往注册中心注册一个实例、再查出来。
 *
 * <p>这是「手动挡」，目的是让你看清 gRPC 客户端最核心的 3 样东西：
 * <ol>
 *   <li><b>Channel（通道）</b>：客户端到 server 的一条连接。类比：打电话得先拨通。</li>
 *   <li><b>Stub（桩）</b>：套在 Channel 上的「遥控器」，proto 里定义的 register/query 等方法都在它身上。</li>
 *   <li><b>调用方法</b>：像调普通 Java 方法一样 stub.register(...)，gRPC 底层帮你把请求发到 server。</li>
 * </ol>
 *
 * <p>这里用的是 <b>BlockingStub（阻塞桩）</b>：调用后原地等结果、直接 return 返回值，
 * 没有 StreamObserver 回调，跟写普通方法一样好懂。（server 端用的是 StreamObserver，因为它要支持流式推送；
 * 客户端简单调用用 BlockingStub 就够了。）
 *
 * <p>运行方式：直接右键 Run 这个类的 main 方法。前提是 server 已启动、监听 9500。
 */
public class SimpleClientDemo {

    public static void main(String[] args) throws Exception {

        // ========== 第 1 步：建立 Channel（拨通到 server 的连接）==========
        // localhost:9500 是你 server 的地址端口；usePlaintext 表示不加密（本地开发用）。
        ManagedChannel channel = NettyChannelBuilder.forAddress("localhost", 9500)
                .usePlaintext()
                .build();

        try {
            // ========== 第 2 步：基于 Channel 造一个 BlockingStub（遥控器）==========
            RegistryServiceGrpc.RegistryServiceBlockingStub stub =
                    RegistryServiceGrpc.newBlockingStub(channel);

            // ========== 第 3 步：注册一个实例 ==========
            // 3.1 先用 Builder 拼出要注册的实例信息（这就是 proto 的 message Instance）
            Instance instance = Instance.newBuilder()
                    .setServiceName("link-restapi")   // 服务名
                    .setIp("192.168.1.100")            // 实例 IP
                    .setPort(8080)                     // 实例端口
                    .setWeight(1.0)                    // 权重
                    .setEphemeral(true)                // 临时实例（连接断/超时即摘除）
                    .build();

            // 3.2 把实例包进 RegisterRequest，调用 register —— 注意：直接 return 结果，像普通方法！
            RegisterRequest registerRequest = RegisterRequest.newBuilder()
                    .setInstance(instance)
                    .build();
            CommonResponse response = stub.register(registerRequest);




            System.out.println("【注册结果】success=" + response.getSuccess()
                    + ", message=" + response.getMessage());

            Iterator<InstanceList> subscribe = stub.subscribe(SubscribeRequest.newBuilder().setServiceName("link-core").build());

            while (subscribe.hasNext()) {
                InstanceList next = subscribe.next();
                List<Instance> instancesList = next.getInstancesList();
                instancesList.forEach(item -> {
                    System.out.println("【订阅事件】item=" + item.getServiceName()
                            + "---" +item.getIp()
                            + ":" +item.getPort());

            });
            }
            // ========== 第 4 步：查询这个服务，验证注册成功 ==========
            QueryRequest queryRequest = QueryRequest.newBuilder()
                    .setServiceName("link-oss")
                    .setOnlyHealthy(true)
                    .build();
            InstanceList list = stub.query(queryRequest);

            System.out.println("【查询结果】link-restapi 当前实例数：" + list.getInstancesCount());
            for (Instance inst : list.getInstancesList()) {
                System.out.println("    -> " + inst.getIp() + ":" + inst.getPort()
                        + " (healthy=" + inst.getHealthy() + ")");
            }

        } finally {
            // ========== 第 5 步：关闭 Channel（挂电话，释放连接）==========
            channel.shutdown();
        }
    }
}
