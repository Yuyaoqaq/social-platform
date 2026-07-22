package com.cy.share.rpc.spring.annotation;

import java.lang.annotation.*;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RpcReference {
    long timeout() default 3000;
    int retryTimes() default 1;
    String version() default "1.0.0";
}
