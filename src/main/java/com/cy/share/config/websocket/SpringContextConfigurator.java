package com.cy.share.config.websocket;

import jakarta.websocket.server.ServerEndpointConfig;
import org.springframework.context.ApplicationContext;

public class SpringContextConfigurator extends ServerEndpointConfig.Configurator {

    private static volatile ApplicationContext applicationContext;

    public static void setApplicationContext(ApplicationContext context) {
        applicationContext = context;
    }

    @Override
    public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
        if (applicationContext == null) {
            throw new IllegalStateException("ApplicationContext not set in SpringContextConfigurator");
        }
        return applicationContext.getBean(endpointClass);
    }
}
