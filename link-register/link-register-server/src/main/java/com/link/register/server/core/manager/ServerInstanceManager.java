package com.link.register.server.core.manager;

import com.link.register.grpc.DeregisterRequest;
import com.link.register.grpc.Instance;
import com.link.register.grpc.InstanceList;
import com.link.register.server.core.ServerInstanceService;
import com.link.register.server.model.InstanceEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月14日
 */
@Component
public class ServerInstanceManager implements ServerInstanceService {

    private static final Logger log = LoggerFactory.getLogger(ServerInstanceManager.class);

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, InstanceEntry>> instances = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, AtomicLong> versions = new ConcurrentHashMap<>();

    private final SubscriberManager subscriberManager;

    public ServerInstanceManager(SubscriberManager subscriberManager) {
        this.subscriberManager = subscriberManager;
    }

    @Override
    public boolean deregister(DeregisterRequest instance) {
        String address = instance.getIp() + ":" + instance.getPort();
        AtomicBoolean removed = new AtomicBoolean(false);
        instances.computeIfPresent(instance.getServiceName(),(k,v) -> {
            if (v.remove(address) != null) {
                removed.set(true);
            }
            return v.isEmpty() ? null : v;
        });
        if (removed.get()){
            bumpVersion(instance.getServiceName());
            subscriberManager.push(instance.getServiceName(), queryList(instance.getServiceName()));
        }
        return removed.get();
    }

    @Override
    public void register(Instance instance) {
        instances.compute(instance.getServiceName(), (k, v) -> {
            if (v == null) {
                v = new ConcurrentHashMap<>();
            }
            // 新建 Entry（带当前时间作为初始心跳）；同 ip:port 再注册即覆盖并刷新时间
            v.put(instance.getIp() + ":" + instance.getPort(),
                    new InstanceEntry(instance, System.currentTimeMillis()));
            return v;
        });
        bumpVersion(instance.getServiceName());
        // 集合变了，推给订阅者
        subscriberManager.push(instance.getServiceName(), queryList(instance.getServiceName()));
    }



    @Override
    public boolean heartbeat(String serverName, String address) {
        ConcurrentHashMap<String, InstanceEntry> inner = instances.get(serverName);
        if (inner == null) {
            return false;
        }
        InstanceEntry entry = inner.get(address);
        if (entry == null) {
            return false;
        }
        entry.refresh(System.currentTimeMillis());   // 只刷新时间，不算集合变化，不用推送
        return true;
    }

    @Override
    public InstanceList queryList(String serverName) {
        ConcurrentHashMap<String, InstanceEntry> inner = this.instances.get(serverName);

        InstanceList.Builder builder = InstanceList.newBuilder()
                .setServiceName(serverName)
                .setLastModified(currentVersion(serverName));

        if (inner != null) {
            for (InstanceEntry entry : inner.values()) {
                // 注册表里的都视为健康（超时的已被健康检查踢除），回填 healthy=true
                builder.addInstances(entry.getInstance().toBuilder().setHealthy(true).build());
            }
        }
        return builder.build();
    }

    @Override
    public Instance queryOne(String serverName, String address) {
        ConcurrentHashMap<String, InstanceEntry> inner = this.instances.get(serverName);
        if (inner == null) {
            return null;
        }
        InstanceEntry entry = inner.get(address);
        return entry == null ? null : entry.getInstance();
    }

    /**
     * 踢除心跳早于 deadline 的实例。
     *
     * @return 本轮被踢除的实例快照列表（含 serviceName / ip / port），供上层打印下线信息。
     *         集合变化的服务会即时把最新列表推给订阅者。
     */
    public List<Instance> evictExpired(long deadline) {
        List<Instance> evicted = new ArrayList<>();
        for (var serviceEntry : instances.entrySet()) {
            String serviceName = serviceEntry.getKey();
            ConcurrentHashMap<String, InstanceEntry> inner = serviceEntry.getValue();

            List<InstanceEntry> expired = new ArrayList<>();
            for (InstanceEntry entry : inner.values()) {
                if (entry.getLastHeartbeat() < deadline) {
                    expired.add(entry);
                }
            }
            if (expired.isEmpty()) {
                continue;
            }
            for (InstanceEntry entry : expired) {
                inner.remove(entry.instanceId());
                evicted.add(entry.getInstance());
            }
            bumpVersion(serviceName);
            // 集合变了：把该服务的最新实例列表推给它的订阅者（即关心它的「其他服务」）
            subscriberManager.push(serviceName, queryList(serviceName));
        }
        return evicted;
    }

    private void bumpVersion(String serverName) {
        versions.computeIfAbsent(serverName, k -> new AtomicLong()).incrementAndGet();
    }

    private long currentVersion(String serverName) {
        AtomicLong v = versions.get(serverName);
        return v == null ? 0L : v.get();
    }
}
