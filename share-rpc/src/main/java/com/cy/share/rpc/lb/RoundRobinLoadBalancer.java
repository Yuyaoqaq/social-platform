package com.cy.share.rpc.lb;

import com.cy.share.rpc.registry.ServiceMeta;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobinLoadBalancer implements LoadBalancer {
    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public ServiceMeta select(List<ServiceMeta> candidates) {
        int index = Math.abs(counter.getAndIncrement() % candidates.size());
        return candidates.get(index);
    }
}
