package com.cy.share.rpc.serialize;

import java.util.ServiceLoader;

public final class SerializerFactory {
    public static Serializer getSerializer(byte type) {
        for (Serializer s : ServiceLoader.load(Serializer.class)) {
            if (s.getType() == type) return s;
        }
        throw new SerializeException("No serializer registered for type: " + type);
    }

    private SerializerFactory() {}
}
