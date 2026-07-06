package com.cy.share.config.websocket;

import jakarta.websocket.server.ServerEndpointConfig;
import org.springframework.context.ApplicationContext;
//SpringContextConfigurator是WebSocket的，用于将Spring容器管理的bean注入到WebSocket中
public class SpringContextConfigurator extends ServerEndpointConfig.Configurator {

    private static volatile ApplicationContext applicationContext;

    public static void setApplicationContext(ApplicationContext context) {
        applicationContext = context;
    }

    //默认情况下，@ServerEndpoint 的实例是 WebSocket 容器自己 new 的，不走 Spring。
    //别自己 new 了，去 Spring 容器里拿
    @Override
    public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
        if (applicationContext == null) {
            throw new IllegalStateException("ApplicationContext not set in SpringContextConfigurator");
        }
        return applicationContext.getBean(endpointClass);
    }
}
