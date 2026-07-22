package com.cy.share.rpc.spring.annotation;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RpcService {
    Class<?> interfaceClass() default void.class; // auto-detect if void
    String version() default "1.0.0";
}
