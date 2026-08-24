package com.cy.share.rpc.protocol;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * JSON header embedded in every RpcMessage.
 * REQUEST fields: serviceName, methodName, paramTypes.
 * RESPONSE fields: success, returnType, errorMessage.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RpcHeader {
    // REQUEST
    private String serviceName;
    private String methodName;
    private String[] paramTypes;

    // RESPONSE
    private boolean success;
    private String returnType;
    private String errorMessage;
}
