package com.cy.share.rpc.demo.provider;

import com.cy.share.rpc.core.server.RpcServer;
import com.cy.share.rpc.demo.api.HelloService;
import com.cy.share.rpc.registry.LocalRegistry;
import com.cy.share.rpc.registry.ServiceMeta;
import com.cy.share.rpc.util.ServiceMap;

public class DemoProvider {
    public static void main(String[] args) throws Exception {
        // 1. Register service implementation in ServiceMap
        HelloServiceImpl impl = new HelloServiceImpl();
        ServiceMap.register(HelloService.class.getName(), impl);

        // 2. Register service address in local registry
        LocalRegistry registry = new LocalRegistry();
        registry.register(new ServiceMeta(HelloService.class.getName(), "127.0.0.1", 20880, 1));

        // 3. Start Netty server
        RpcServer server = new RpcServer(20880);
        server.start();
        System.out.println("[Provider] Started. Listening on :20880");
        System.out.println("[Provider] Press Ctrl+C to stop.");
        server.waitForShutdown();
    }
}
