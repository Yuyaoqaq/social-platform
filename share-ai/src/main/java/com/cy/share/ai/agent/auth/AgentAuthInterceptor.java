package com.cy.share.ai.agent.auth;

import com.cy.share.api.UserAuthService;
import com.cy.share.common.constant.ResultCode;
import com.cy.share.common.utils.Result;
import com.cy.share.common.utils.UserContext;
import com.cy.share.dto.AuthValidationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * Agent 接口认证拦截器，对所有 /agent/** 请求从 Authorization 头提取 Bearer Token，
 * 通过 Dubbo 调用统一认证服务校验登录态，成功后将 userId 写入 UserContext。
 */
@Component
@RequiredArgsConstructor
public class AgentAuthInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;
    //第一个属性：是检查提供者是否存在，如果不存在也不要报错
    @DubboReference(check = false, timeout = 3000)
    private UserAuthService userAuthService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String authorization = request.getHeader("Authorization");
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            writeUnauthorized(response, "请先登录");
            return false;
        }

        AuthValidationResult validation = userAuthService.validateAccessToken(authorization.substring(7));
        if (!validation.isValid()) {
            writeUnauthorized(response, validation.getMessage());
            return false;
        }

        UserContext.set(validation.getUserId());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.remove();
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        //objectMapper.writeValue(管道, 对象) → 把对象转成 JSON 并写进管道
        objectMapper.writeValue(response.getWriter(), Result.fail(ResultCode.UNAUTHORIZED, message));
    }
}
