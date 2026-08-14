package com.cy.share.ai.agent.auth;

import com.cy.share.ai.agent.config.AgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * Agent 固定时间窗口限流服务，基于 Redis INCR 实现，对对话接口按分钟限流、生图接口按天限流。
 * Redis 不可用时自动放行（fail-open），避免因限流故障阻塞正常请求。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRateLimitService {
    //固定时间窗口限流
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final StringRedisTemplate redisTemplate;
    private final AgentProperties properties;

    public boolean allowChat(String userId) {
        String key = "agent:rate:chat:" + userId + ":" + MINUTE.format(LocalDateTime.now());
        return incrementAndCheck(key, properties.getRateLimitPerMinute(), 2, TimeUnit.MINUTES);
    }

    public boolean allowImage(String userId) {
        String key = "agent:rate:image:" + userId + ":" + LocalDate.now();
        return incrementAndCheck(key, properties.getImageLimitPerDay(), 2, TimeUnit.DAYS);
    }

    private boolean incrementAndCheck(String key, int limit, long ttl, TimeUnit unit) {
        try {
            //拿到这个值，并加1，如果这个值不存在就创建一个新的key，值为1
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, ttl, unit);
            }
            return count == null || count <= limit;
        } catch (Exception e) {
            log.warn("Agent rate limit unavailable, fail open. key={}", key, e);
            return true;
        }
    }
}
