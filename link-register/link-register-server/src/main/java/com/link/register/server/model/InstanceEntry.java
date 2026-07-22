package com.link.register.server.model;

import com.link.register.grpc.Instance;


public class InstanceEntry {

    /** 客户端上报的实例信息（protobuf 不可变对象，作为静态快照持有） */
    private final Instance instance;

    /** 最后一次心跳的时间戳（毫秒）。volatile：心跳线程写、健康检查线程读，保证可见性 */
    private volatile long lastHeartbeat;

    public InstanceEntry(Instance instance, long now) {
        this.instance = instance;
        this.lastHeartbeat = now;
    }

    /** 实例唯一标识：ip:port */
    public static String idOf(String ip, int port) {
        return ip + ":" + port;
    }

    public String instanceId() {
        return idOf(instance.getIp(), instance.getPort());
    }

    public Instance getInstance() {
        return instance;
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    /** 收到心跳时刷新存活时间 */
    public void refresh(long now) {
        this.lastHeartbeat = now;
    }
}
