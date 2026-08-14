package com.cy.share.ai.agent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

/**
 * Spring AI 基础设施配置，提供全局 ChatClient Bean，以及针对 DashScope/Qdrant 等外部 HTTP 调用的超时设置。
 */
@Configuration
public class SpringAiConfig {

    /**
     * 延长调用 DashScope 等外部 HTTP 服务（生图、搜索、Qdrant）的连接和读取时间。
     */
    @Bean
    RestClientCustomizer restClientTimeoutCustomizer() {
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            //建立连接的超时时间
            factory.setConnectTimeout(Duration.ofSeconds(15));
            //读取数据的超时时间
            factory.setReadTimeout(Duration.ofSeconds(120));
            builder.requestFactory(factory);
        };
    }

    @Bean
    ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
