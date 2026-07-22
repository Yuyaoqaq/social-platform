package com.cy.share.rpc.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class LocalRegistry implements Registry {
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<ServiceMeta>> store =
            new ConcurrentHashMap<>();

    @Override
    public void register(ServiceMeta meta) {
        store.computeIfAbsent(meta.getServiceName(), k -> new CopyOnWriteArrayList<>()).add(meta);
    }

    @Override
    public void unregister(ServiceMeta meta) {
        CopyOnWriteArrayList<ServiceMeta> list = store.get(meta.getServiceName());
        if (list != null) list.remove(meta);
    }

    @Override
    public List<ServiceMeta> discover(String serviceName) {
        CopyOnWriteArrayList<ServiceMeta> list = store.get(serviceName);
        return list == null ? List.of() : new ArrayList<>(list);
    }
}
