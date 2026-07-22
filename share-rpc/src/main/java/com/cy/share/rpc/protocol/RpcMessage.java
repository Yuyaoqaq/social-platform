package com.cy.share.rpc.protocol;

import lombok.Data;

@Data
public class RpcMessage {
    private byte version;
    private byte messageType;    // MessageType.code
    private byte serializeType;  // SerializeType.code
    private long requestId;
    private RpcHeader header;    // deserialized from the variable JSON section
    private byte[] body;         // raw serialized params or return value (may be null for heartbeat)
}
