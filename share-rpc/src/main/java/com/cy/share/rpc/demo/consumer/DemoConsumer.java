package com.cy.share.rpc.demo.consumer;

import com.cy.share.rpc.core.client.RpcClient;
import com.cy.share.rpc.demo.api.HelloService;
import com.cy.share.rpc.registry.LocalRegistry;
import com.cy.share.rpc.registry.ServiceMeta;

public class DemoConsumer {
    public static void main(String[] args) throws Exception {
        // 1. Build a local registry with the provider's address
        LocalRegistry registry = new LocalRegistry();
        registry.register(new ServiceMeta(HelloService.class.getName(), "127.0.0.1", 20880, 1));

        // 2. Create RPC client and get a proxy
        try (RpcClient client = new RpcClient(registry)) {
            HelloService hello = client.create(HelloService.class);

            // 3. Call the remote service (looks like a local call)
            String result = hello.sayHello("World");
            System.out.println("[Consumer] RPC result: " + result);

            // 4. Call again to verify channel reuse
            result = hello.sayHello("RPC");
            System.out.println("[Consumer] RPC result: " + result);
        }
        System.out.println("[Consumer] Done.");
    }
}
