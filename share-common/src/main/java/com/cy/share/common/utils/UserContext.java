package com.cy.share.common.utils;

public class UserContext {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    public static void set(String userId) {
        HOLDER.set(userId);
    }

    public static String get() {
        return HOLDER.get();
    }

    public static void remove() {
        HOLDER.remove();
    }
}
