package com.cy.share.ai;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 启动入口，负责初始化 Spring 容器、Dubbo 服务提供者和 MyBatis Mapper 扫描。
 */
@SpringBootApplication
@EnableDubbo
@MapperScan({"com.cy.share.ai.mapper", "com.cy.share.ai.agent.memory", "com.cy.share.ai.agent.trace"})
public class AiApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiApplication.class, args);
    }
}
