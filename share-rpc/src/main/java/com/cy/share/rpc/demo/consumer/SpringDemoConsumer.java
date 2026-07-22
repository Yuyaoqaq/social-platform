package com.cy.share.rpc.demo.consumer;

import com.cy.share.rpc.demo.api.HelloService;
import com.cy.share.rpc.spring.annotation.RpcReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

@SpringBootApplication
public class SpringDemoConsumer {
    public static void main(String[] args) {
        SpringApplication.run(SpringDemoConsumer.class, args);
    }

    @Slf4j
    @Component
    static class HelloRunner implements CommandLineRunner {
        @RpcReference(timeout = 3000)
        private HelloService helloService;

        @Override
        public void run(String... args) throws Exception {
            String result = helloService.sayHello("SpringWorld");
            log.info("[Spring Consumer] result: {}", result);
        }
    }
}
