package com.link.register.server.grpc;

import com.link.register.grpc.*;
import com.link.register.server.core.manager.ServerInstanceManager;
import com.link.register.server.core.manager.SubscriberManager;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RegistryServiceImpl extends RegistryServiceGrpc.RegistryServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(RegistryServiceImpl.class);

    @Autowired
    private ServerInstanceManager serverInstanceManager;

    @Autowired
    private SubscriberManager subscriberManager;

    @Override
    public void register(RegisterRequest request, StreamObserver<CommonResponse> responseObserver) {
        Instance instance = request.getInstance();
        if (instance != null) {
            serverInstanceManager.register(instance);
            responseObserver.onNext(ok("注册成功"));
        } else {
            responseObserver.onNext(fail("注册失败"));
        }
        responseObserver.onCompleted();
    }

    @Override
    public void deregister(DeregisterRequest request, StreamObserver<CommonResponse> responseObserver) {
        if (serverInstanceManager.deregister(request)) {
            responseObserver.onNext(ok("删除成功"));
        }
    }

    @Override
    public void heartbeat(HeartbeatRequest request, StreamObserver<CommonResponse> responseObserver) {
        String address = request.getIp() + ":" + request.getPort();
        boolean alive = serverInstanceManager.heartbeat(request.getServiceName(), address);
        if (alive) {
            responseObserver.onNext(ok("beat"));
        } else {
            responseObserver.onNext(fail("instance not found, please re-register"));
        }
        responseObserver.onCompleted();
    }

    @Override
    public void query(QueryRequest request, StreamObserver<InstanceList> responseObserver) {
        // query 接口的语义 = 查某个服务的全部实例（场景1，给负载均衡）。
        // queryList 保证返回非 null（没实例时返回空列表），所以不用判空。
        InstanceList result = serverInstanceManager.queryList(request.getServiceName());
        // unary（一问一答）：发一次 + 收尾，两个都不能少，否则客户端会一直等到超时
        responseObserver.onNext(result);
        responseObserver.onCompleted();
    }


    @Override
    public void subscribe(SubscribeRequest request, StreamObserver<InstanceList> responseObserver) {
        String serviceName = request.getServiceName();
        subscriberManager.subscribe(serviceName, responseObserver);
        responseObserver.onNext(serverInstanceManager.queryList(serviceName));
        if (responseObserver instanceof ServerCallStreamObserver<InstanceList> serverObserver) {
            serverObserver.setOnCancelHandler(() -> {
                subscriberManager.unsubscribe(serviceName, responseObserver);
                log.info("订阅取消: service={}", serviceName);
            });
        }
    }



    private static CommonResponse ok(String msg) {
        return CommonResponse.newBuilder().setSuccess(true).setMessage(msg).build();
    }

    private static CommonResponse fail(String msg) {
        return CommonResponse.newBuilder().setSuccess(false).setMessage(msg == null ? "" : msg).build();
    }
}
