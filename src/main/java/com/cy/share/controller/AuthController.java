package com.cy.share.controller;

import com.cy.share.common.annotation.UnInterception;
import com.cy.share.common.constant.Jwtconstant;
import com.cy.share.common.constant.RedisConstant;
import com.cy.share.common.constant.ResultCode;
import com.cy.share.common.utils.JwtUtil;
import com.cy.share.common.utils.Result;
import com.cy.share.common.utils.RsaKeyHolder;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RsaKeyHolder rsaKeyHolder;
    private final StringRedisTemplate redisTemplate;

    @UnInterception
    @PostMapping("/refresh")
    public Result refresh(HttpServletRequest request) {
        String refreshToken = extractRefreshCookie(request);
        if (refreshToken == null) {
            return Result.fail(ResultCode.UNAUTHORIZED, "登录已失效，请重新登录");
        }

        String key = RedisConstant.REFRESH_TOKEN_PREFIX + refreshToken;
        String userId = redisTemplate.opsForValue().get(key);
        if (userId == null) {
            return Result.fail(ResultCode.UNAUTHORIZED, "登录已失效，请重新登录");
        }

        // 滑动续期
        redisTemplate.expire(key, Jwtconstant.REFRESH_TOKEN_TTL, TimeUnit.SECONDS);

        String newAccessToken = JwtUtil.createAccessToken(userId, rsaKeyHolder.getPrivateKey());
        return Result.success(newAccessToken);
    }

    private String extractRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if ("refreshToken".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
