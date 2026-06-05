package com.cy.share.service.impl;

import com.cy.share.common.constant.RedisConstant;
import com.cy.share.service.CodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class CodeServiceImpl implements CodeService {

    private static final long TTL_MINUTES = 5;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Override
    public void save(String phone, String code) {
        redisTemplate.opsForValue().set(RedisConstant.SMS_CODE_PREFIX + phone, code, TTL_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public boolean verify(String phone, String code) {
        String stored = redisTemplate.opsForValue().get(RedisConstant.SMS_CODE_PREFIX + phone);
        if (stored == null || !stored.equals(code)) return false;
        redisTemplate.delete(RedisConstant.SMS_CODE_PREFIX + phone);
        return true;
    }
}
