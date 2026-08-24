package com.cy.share.rpc.registry;

public class RegistryException extends RuntimeException {
    public RegistryException(String message, Throwable cause) { super(message, cause); }
    public RegistryException(String message) { super(message); }
}
