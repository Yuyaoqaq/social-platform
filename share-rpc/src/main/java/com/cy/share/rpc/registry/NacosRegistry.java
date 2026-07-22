package com.cy.share.rpc.registry;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
public class NacosRegistry implements Registry {
    private static final String CLUSTER = "share-rpc";
    private final NamingService namingService;

    public NacosRegistry(String serverAddr) {
        try {
            this.namingService = NamingFactory.createNamingService(serverAddr);
        } catch (NacosException e) {
            throw new RegistryException("Failed to connect to Nacos: " + serverAddr, e);
        }
    }

    @Override
    public void register(ServiceMeta meta) {
        try {
            namingService.registerInstance(meta.getServiceName(), meta.getHost(), meta.getPort(), CLUSTER);
            log.info("Registered {} at {}:{}", meta.getServiceName(), meta.getHost(), meta.getPort());
        } catch (NacosException e) {
            throw new RegistryException("Nacos register failed for: " + meta.getServiceName(), e);
        }
    }

    @Override
    public void unregister(ServiceMeta meta) {
        try {
            namingService.deregisterInstance(meta.getServiceName(), meta.getHost(), meta.getPort(), CLUSTER);
        } catch (NacosException e) {
            throw new RegistryException("Nacos unregister failed for: " + meta.getServiceName(), e);
        }
    }

    @Override
    public List<ServiceMeta> discover(String serviceName) {
        try {
            return namingService.getAllInstances(serviceName, CLUSTER).stream()
                    .filter(Instance::isEnabled)
                    .filter(Instance::isHealthy)
                    .map(i -> new ServiceMeta(serviceName, i.getIp(), i.getPort(),
                            (int) i.getWeight()))
                    .collect(Collectors.toList());
        } catch (NacosException e) {
            throw new RegistryException("Nacos discover failed for: " + serviceName, e);
        }
    }

    @Override
    public void subscribe(String serviceName, Consumer<List<ServiceMeta>> listener) {
        try {
            namingService.subscribe(serviceName, CLUSTER, event -> {
                try {
                    listener.accept(discover(serviceName));
                } catch (Exception e) {
                    log.error("Nacos subscription callback error", e);
                }
            });
        } catch (NacosException e) {
            throw new RegistryException("Nacos subscribe failed for: " + serviceName, e);
        }
    }

    @Override
    public void close() {
        try {
            namingService.shutDown();
        } catch (NacosException e) {
            log.warn("Error shutting down Nacos client", e);
        }
    }
}
