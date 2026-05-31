package com.cy.share.controller.login;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cy.share.common.annotation.UnInterception;
import com.cy.share.common.constant.Jwtconstant;
import com.cy.share.common.constant.RedisConstant;
import com.cy.share.common.constant.ResultCode;
import com.cy.share.common.exception.CodeWrongException;
import com.cy.share.common.exception.UserRegistException;
import com.cy.share.common.exception.UserSaveException;
import com.cy.share.common.utils.JwtUtil;
import com.cy.share.common.utils.Result;
import com.cy.share.common.utils.RsaKeyHolder;
import com.cy.share.pojo.User;
import com.cy.share.service.CodeService;
import com.cy.share.service.UserService;
import com.cy.share.vo.LoginVo;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
public class LoginController {

    private final UserService userService;
    private final CodeService codeService;
    private final RsaKeyHolder rsaKeyHolder;
    private final StringRedisTemplate redisTemplate;

    @UnInterception
    @PostMapping("/login")
    public Result login(@RequestBody User data, HttpServletResponse response) {
        User user = userService.getOne(new QueryWrapper<User>()
                .eq("name", data.getName())
                .eq("pwd", data.getPwd())
                .last("LIMIT 1"));
        if (user == null) return Result.fail(ResultCode.DATA_NOT_FOUND);
        return buildTokenResponse(user, response);
    }

    @UnInterception
    @PostMapping("/register/send-code")
    public Result sendCode(@RequestBody Map<String, String> params) {
        String phone = params.get("phone");
        User user = userService.getOne(new QueryWrapper<User>()
                .eq("phone", phone)
                .last("LIMIT 1"));
        if (user != null) throw new UserRegistException();
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
        codeService.save(phone, code);
        return Result.success(code);
    }

    @UnInterception
    @PostMapping("/register")
    public Result regist(@RequestBody Map<String, String> params) {
        User existing = userService.getOne(new QueryWrapper<User>()
                .eq("phone", params.get("phone"))
                .last("LIMIT 1"));
        if (existing != null) throw new UserRegistException();
        boolean ok = codeService.verify(params.get("phone"), params.get("code"));
        if (!ok) throw new CodeWrongException();
        return Result.success();
    }

    @UnInterception
    @PostMapping("/registerover")
    public Result registerOver(@RequestBody User data, HttpServletResponse response) {
        boolean save = userService.save(data);
        if (!save) throw new UserSaveException();
        return buildTokenResponse(data, response);
    }

    private Result buildTokenResponse(User user, HttpServletResponse response) {
        String userId = String.valueOf(user.getId());
        String accessToken = JwtUtil.createAccessToken(userId, rsaKeyHolder.getPrivateKey());

        String refreshToken = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(
                RedisConstant.REFRESH_TOKEN_PREFIX + refreshToken,
                userId,
                Jwtconstant.REFRESH_TOKEN_TTL,
                TimeUnit.SECONDS
        );
        writeRefreshCookie(response, refreshToken);

        LoginVo loginVo = new LoginVo();
        BeanUtils.copyProperties(user, loginVo);
        loginVo.setAccessToken(accessToken);
        return Result.success(loginVo);
    }

    private void writeRefreshCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie("refreshToken", refreshToken);
        cookie.setHttpOnly(true);
        cookie.setPath("/auth/refresh");
        cookie.setMaxAge((int) Jwtconstant.REFRESH_TOKEN_TTL);
        response.addCookie(cookie);
    }
}
