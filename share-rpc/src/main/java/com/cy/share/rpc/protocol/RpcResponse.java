package com.cy.share.rpc.protocol;

import lombok.Data;

@Data
public class RpcResponse {
    private long requestId;
    private boolean success;
    private byte[] body;          // serialized return value
    private String returnType;    // fully-qualified class name for deserialization
    private String errorMessage;
}
