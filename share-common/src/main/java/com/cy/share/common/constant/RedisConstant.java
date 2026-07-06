package com.cy.share.common.constant;

public class RedisConstant {
    public static final String SMS_CODE_PREFIX      = "sms:code:";
    public static final String REFRESH_TOKEN_PREFIX = "auth:refresh:";
    public static final String LIKE_LOG_PREFIX      = "like:log:";
    public static final String LIKE_USER_PREFIX     = "like:user:";
    public static final String LIKE_LOCK_LOG_PREFIX  = "like:lock:log:";
    public static final String LIKE_LOCK_USER_PREFIX = "like:lock:user:";
    /** ZSet 哨兵值——标记"确认过该 ZSet 无数据"，存为 member="-1", score=0 */
    public static final String LIKE_SENTINEL         = "-1";

    /** ZSet TTL 基础秒数（24h） */
    public static final int LIKE_TTL_BASE_SECONDS = 24 * 60 * 60;
    /** ZSet TTL 随机偏移上限秒数（2h），防缓存雪崩 */
    public static final int LIKE_TTL_RANDOM_SECONDS = 2 * 60 * 60;
}
