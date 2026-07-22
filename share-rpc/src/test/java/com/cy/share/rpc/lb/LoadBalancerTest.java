package com.cy.share.rpc.lb;

import com.cy.share.rpc.registry.ServiceMeta;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LoadBalancerTest {
    private final List<ServiceMeta> candidates = List.of(
            new ServiceMeta("svc", "host1", 1, 1),
            new ServiceMeta("svc", "host2", 2, 1),
            new ServiceMeta("svc", "host3", 3, 1)
    );

    @Test
    void randomSelectsFromCandidates() {
        RandomLoadBalancer lb = new RandomLoadBalancer();
        for (int i = 0; i < 20; i++) {
            ServiceMeta selected = lb.select(candidates);
            assertTrue(candidates.contains(selected));
        }
    }

    @Test
    void roundRobinCyclesThroughAll() {
        RoundRobinLoadBalancer lb = new RoundRobinLoadBalancer();
        ServiceMeta first  = lb.select(candidates);
        ServiceMeta second = lb.select(candidates);
        ServiceMeta third  = lb.select(candidates);
        ServiceMeta fourth = lb.select(candidates);
        assertNotEquals(first.getHost(), second.getHost());
        assertNotEquals(second.getHost(), third.getHost());
        assertEquals(first.getHost(), fourth.getHost()); // wraps around
    }
}
