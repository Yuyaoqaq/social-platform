package com.cy.share.rpc.protocol;

import lombok.Data;

@Data
public class RpcRequest {
    private long requestId;
    private String serviceName;
    private String methodName;
    private Class<?>[] paramTypes;
    private Object[] params;
}
