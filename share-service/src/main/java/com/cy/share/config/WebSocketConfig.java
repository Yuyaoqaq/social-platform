package com.cy.share.config;

import com.cy.share.config.websocket.SpringContextConfigurator;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

@Configuration
public class WebSocketConfig {
    /**
     * 该方法将 Spring 容器管理的 bean 注入到 SpringContextConfigurator 中
     */
    public WebSocketConfig(ApplicationContext applicationContext) {
        SpringContextConfigurator.setApplicationContext(applicationContext);
    }
    // 让 @ServerEndpoint 归 Spring 管
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}
