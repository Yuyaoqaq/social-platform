package com.cy.share.rpc.lb;

import com.cy.share.rpc.registry.ServiceMeta;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RandomLoadBalancer implements LoadBalancer {
    @Override
    public ServiceMeta select(List<ServiceMeta> candidates) {
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }
}
