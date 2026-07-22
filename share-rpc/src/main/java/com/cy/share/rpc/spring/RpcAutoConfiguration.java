package com.cy.share.rpc.spring;

import com.cy.share.rpc.core.client.RpcClient;
import com.cy.share.rpc.core.server.RpcServer;
import com.cy.share.rpc.lb.LoadBalancer;
import com.cy.share.rpc.lb.RandomLoadBalancer;
import com.cy.share.rpc.registry.LocalRegistry;
import com.cy.share.rpc.registry.NacosRegistry;
import com.cy.share.rpc.registry.Registry;
import com.cy.share.rpc.registry.ServiceMeta;
import com.cy.share.rpc.retry.FailFastPolicy;
import com.cy.share.rpc.serialize.JsonSerializer;
import com.cy.share.rpc.serialize.Serializer;
import com.cy.share.rpc.spring.annotation.RpcService;
import com.cy.share.rpc.util.ServiceMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Slf4j
@Configuration
@EnableConfigurationProperties(RpcProperties.class)
@ConditionalOnProperty(prefix = "share.rpc", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RpcAutoConfiguration {

    @Bean
    public Registry rpcRegistry(RpcProperties props) {
        String type = props.getRegistry().getType();
        if ("nacos".equalsIgnoreCase(type)) {
            String addr = props.getRegistry().getNacos().getServerAddr();
            log.info("RPC registry: Nacos at {}", addr);
            return new NacosRegistry(addr);
        }
        log.info("RPC registry: Local (in-memory)");
        return new LocalRegistry();
    }

    @Bean
    public LoadBalancer rpcLoadBalancer() {
        return new RandomLoadBalancer();
    }

    @Bean
    public Serializer rpcSerializer() {
        return new JsonSerializer();
    }

    @Bean
    public RpcClient rpcClient(Registry registry, LoadBalancer lb, Serializer serializer,
                                RpcProperties props) {
        return new RpcClient(registry, lb, serializer,
                props.getConsumer().getTimeout(),
                props.getConsumer().getRetryTimes(),
                new FailFastPolicy());
    }

    @Bean
    public RpcBeanPostProcessor rpcBeanPostProcessor(RpcClient rpcClient) {
        return new RpcBeanPostProcessor(rpcClient);
    }

    @Bean(initMethod = "start", destroyMethod = "close")
    public RpcServer rpcServer(Registry registry, RpcProperties props,
                               ApplicationContext ctx) {
        RpcProperties.Provider providerCfg = props.getProvider();

        // Register all @RpcService beans into the registry
        Map<String, Object> beans = ctx.getBeansWithAnnotation(RpcService.class);
        for (Object bean : beans.values()) {
            RpcService anno = bean.getClass().getAnnotation(RpcService.class);
            Class<?> iface = anno.interfaceClass();
            if (iface == void.class) {
                iface = bean.getClass().getInterfaces()[0];
            }
            ServiceMeta meta = new ServiceMeta(iface.getName(), providerCfg.getHost(),
                    providerCfg.getPort(), 1);
            registry.register(meta);
            log.info("Auto-registered {} -> {}:{}", iface.getName(), providerCfg.getHost(), providerCfg.getPort());
        }

        return new RpcServer(providerCfg.getPort());
    }
}
