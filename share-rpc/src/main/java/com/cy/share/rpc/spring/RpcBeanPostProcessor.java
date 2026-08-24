package com.cy.share.rpc.spring;

import com.cy.share.rpc.core.client.RpcClient;
import com.cy.share.rpc.spring.annotation.RpcReference;
import com.cy.share.rpc.spring.annotation.RpcService;
import com.cy.share.rpc.util.ServiceMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.lang.reflect.Field;

@Slf4j
public class RpcBeanPostProcessor implements BeanPostProcessor {
    private final RpcClient rpcClient;

    public RpcBeanPostProcessor(RpcClient rpcClient) {
        this.rpcClient = rpcClient;
    }

    /** Scan @RpcService — register into ServiceMap before bean init */
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        RpcService anno = bean.getClass().getAnnotation(RpcService.class);
        if (anno != null) {
            Class<?> iface = anno.interfaceClass();
            if (iface == void.class) {
                Class<?>[] interfaces = bean.getClass().getInterfaces();
                if (interfaces.length == 0) {
                    throw new IllegalStateException(bean.getClass() + " has @RpcService but no interfaces");
                }
                iface = interfaces[0];
            }
            ServiceMap.register(iface.getName(), bean);
            log.info("Registered RPC service: {}", iface.getName());
        }
        return bean;
    }

    /** Scan @RpcReference fields — inject proxy after bean init */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        for (Field field : bean.getClass().getDeclaredFields()) {
            RpcReference anno = field.getAnnotation(RpcReference.class);
            if (anno != null) {
                Object proxy = rpcClient.create(field.getType());
                field.setAccessible(true);
                try {
                    field.set(bean, proxy);
                } catch (IllegalAccessException e) {
                    throw new BeansException("Failed to inject @RpcReference into " + field, e) {};
                }
                log.info("Injected @RpcReference proxy for: {}", field.getType().getName());
            }
        }
        return bean;
    }
}
