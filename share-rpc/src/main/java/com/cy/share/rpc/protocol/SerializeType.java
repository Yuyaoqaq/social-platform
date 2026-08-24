package com.cy.share.rpc.protocol;

import lombok.Getter;

@Getter
public enum SerializeType {
    JSON((byte) 0x01),
    KRYO((byte) 0x02);

    private final byte code;

    SerializeType(byte code) { this.code = code; }

    public static SerializeType of(byte code) {
        for (SerializeType t : values()) {
            if (t.code == code) return t;
        }
        throw new IllegalArgumentException("Unknown serialize type: " + code);
    }
}
