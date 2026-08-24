package com.cy.share.rpc.serialize;

public interface Serializer {
    /** Returns the serialize type code matching SerializeType enum */
    byte getType();

    /** Serialize object to bytes */
    <T> byte[] serialize(T obj) throws SerializeException;

    /** Deserialize bytes to typed object */
    <T> T deserialize(byte[] data, Class<T> clazz) throws SerializeException;
}
