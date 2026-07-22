package com.link.register.server.core;

import com.link.register.grpc.DeregisterRequest;
import com.link.register.grpc.Instance;
import com.link.register.grpc.InstanceList;


public interface ServerInstanceService {

    void register(Instance instance);

    boolean deregister(DeregisterRequest instance);

    boolean heartbeat(String serverName, String address);

    InstanceList queryList(String serverName);

    Instance queryOne(String serverName, String address);
}
