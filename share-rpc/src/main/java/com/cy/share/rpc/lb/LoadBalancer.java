package com.cy.share.rpc.lb;

import com.cy.share.rpc.registry.ServiceMeta;
import java.util.List;

public interface LoadBalancer {
    ServiceMeta select(List<ServiceMeta> candidates);
}
