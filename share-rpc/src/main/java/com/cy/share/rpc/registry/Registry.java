package com.cy.share.rpc.registry;

import java.util.List;
import java.util.function.Consumer;

public interface Registry {
    void register(ServiceMeta meta) throws RegistryException;
    void unregister(ServiceMeta meta) throws RegistryException;
    List<ServiceMeta> discover(String serviceName) throws RegistryException;
    default void subscribe(String serviceName, Consumer<List<ServiceMeta>> listener) {}
    default void close() {}
}
