package com.link.register.server.core.manager;

import com.link.register.grpc.InstanceList;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


@Component
public class SubscriberManager {

    private static final Logger log = LoggerFactory.getLogger(SubscriberManager.class);

    private final Map<String, Set<StreamObserver<InstanceList>>> subscribers = new ConcurrentHashMap<>();

    public void subscribe(String serviceName, StreamObserver<InstanceList> observer) {
        subscribers.computeIfAbsent(serviceName, k -> ConcurrentHashMap.newKeySet()).add(observer);
        log.info("新增订阅: service={}, 当前订阅数={}", serviceName, subscribers.get(serviceName).size());
    }

    public void unsubscribe(String serviceName, StreamObserver<InstanceList> observer) {
        Set<StreamObserver<InstanceList>> set = subscribers.get(serviceName);
        if (set != null) {
            set.remove(observer);
        }
    }

    public void push(String serviceName, InstanceList snapshot) {
        Set<StreamObserver<InstanceList>> set = subscribers.get(serviceName);
        if (set == null || set.isEmpty()) {
            return;
        }
        for (StreamObserver<InstanceList> observer : set) {
            try {
                observer.onNext(snapshot);
            } catch (Exception e) {
                log.warn("推送失败，移除订阅者: service={}, err={}", serviceName, e.toString());
                set.remove(observer);
            }
        }
    }
}
