package com.cy.share.rpc.protocol;

import lombok.Getter;

@Getter
public enum MessageType {
    REQUEST((byte) 0x01),
    RESPONSE((byte) 0x02),
    HEARTBEAT((byte) 0x03);

    private final byte code;

    MessageType(byte code) { this.code = code; }

    public static MessageType of(byte code) {
        for (MessageType t : values()) {
            if (t.code == code) return t;
        }
        throw new IllegalArgumentException("Unknown message type: " + code);
    }
}
