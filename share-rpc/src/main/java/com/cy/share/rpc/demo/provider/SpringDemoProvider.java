package com.cy.share.rpc.demo.provider;

import com.cy.share.rpc.demo.api.HelloService;
import com.cy.share.rpc.spring.annotation.RpcService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

@SpringBootApplication
public class SpringDemoProvider {
    public static void main(String[] args) {
        SpringApplication.run(SpringDemoProvider.class, args);
    }

    @RpcService(interfaceClass = HelloService.class)
    @Component
    static class HelloServiceBean implements HelloService {
        @Override
        public String sayHello(String name) {
            return "Hello (Spring), " + name + "!";
        }
    }
}
