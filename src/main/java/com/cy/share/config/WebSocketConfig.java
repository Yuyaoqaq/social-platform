package com.cy.share.config;

import com.cy.share.config.websocket.SpringContextConfigurator;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

@Configuration
public class WebSocketConfig {

    public WebSocketConfig(ApplicationContext applicationContext) {
        SpringContextConfigurator.setApplicationContext(applicationContext);
    }

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}
