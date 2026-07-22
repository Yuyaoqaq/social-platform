package com.cy.share.rpc.util;

import java.util.concurrent.ConcurrentHashMap;

public final class ServiceMap {
    private static final ConcurrentHashMap<String, Object> MAP = new ConcurrentHashMap<>();

    public static void register(String serviceName, Object bean) {
        MAP.put(serviceName, bean);
    }

    public static Object get(String serviceName) {
        return MAP.get(serviceName);
    }

    private ServiceMap() {}
}
