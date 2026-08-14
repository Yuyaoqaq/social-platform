package com.cy.share.ai.agent.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Agent 运行时配置，启用 AgentProperties 属性绑定，并提供 SSE 异步响应专用的线程池 agentExecutor。
 */
@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class AgentRuntimeConfig {
    //sse异步响应线程池
    @Bean("agentExecutor")
    public Executor agentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(12);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("agent-");
        executor.initialize();
        return executor;
    }
}
