package com.cy.share.rpc.registry;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServiceMeta {
    private String serviceName;
    private String host;
    private int    port;
    private int    weight; // used by WeightedRandomLoadBalancer; default 1
}
