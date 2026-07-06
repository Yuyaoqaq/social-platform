package com.cy.share.common.constant;

public class Jwtconstant {
    public static final int JWT_ERRCODE_NULL   = 4000;  // Token 不存在
    public static final int JWT_ERRCODE_EXPIRE = 4001;  // Token 过期
    public static final int JWT_ERRCODE_FAIL   = 4002;  // 验证不通过

    public static final long ACCESS_TOKEN_TTL  = 60 * 60 * 1000L;    // 1小时（毫秒）
    public static final long REFRESH_TOKEN_TTL = 7 * 24 * 60 * 60L;  // 7天（秒，给 Redis TTL 用）
}
