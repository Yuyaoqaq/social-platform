package com.cy.share.rpc.lb;

import com.cy.share.rpc.registry.ServiceMeta;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class WeightedRandomLoadBalancer implements LoadBalancer {
    @Override
    public ServiceMeta select(List<ServiceMeta> candidates) {
        int totalWeight = candidates.stream().mapToInt(ServiceMeta::getWeight).sum();
        int rand = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;
        for (ServiceMeta meta : candidates) {
            cumulative += meta.getWeight();
            if (rand < cumulative) return meta;
        }
        return candidates.get(candidates.size() - 1);
    }
}
