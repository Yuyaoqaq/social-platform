package com.cy.share.rpc.serialize;

public class SerializeException extends RuntimeException {
    public SerializeException(String message, Throwable cause) { super(message, cause); }
    public SerializeException(String message) { super(message); }
}
