package com.cy.share.rpc.serialize;

import com.cy.share.rpc.protocol.SerializeType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonSerializer implements Serializer {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public byte getType() {
        return SerializeType.JSON.getCode();
    }

    @Override
    public <T> byte[] serialize(T obj) throws SerializeException {
        try {
            return MAPPER.writeValueAsBytes(obj);
        } catch (Exception e) {
            throw new SerializeException("JSON serialize failed", e);
        }
    }

    @Override
    public <T> T deserialize(byte[] data, Class<T> clazz) throws SerializeException {
        try {
            return MAPPER.readValue(data, clazz);
        } catch (Exception e) {
            throw new SerializeException("JSON deserialize failed for type " + clazz.getName(), e);
        }
    }

    /** Convenience: deserialize a JSON array node-by-node into typed params */
    public static Object[] deserializeParams(byte[] body, Class<?>[] types) throws SerializeException {
        try {
            if (body == null || body.length == 0 || types.length == 0) return new Object[0];
            JsonNode[] nodes = MAPPER.readValue(body, JsonNode[].class);
            Object[] result = new Object[types.length];
            for (int i = 0; i < types.length; i++) {
                result[i] = MAPPER.treeToValue(nodes[i], types[i]);
            }
            return result;
        } catch (Exception e) {
            throw new SerializeException("JSON param deserialize failed", e);
        }
    }
}
