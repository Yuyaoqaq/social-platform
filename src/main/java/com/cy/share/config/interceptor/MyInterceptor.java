package com.cy.share.config.interceptor;

import com.cy.share.common.annotation.UnInterception;
import com.cy.share.common.constant.Jwtconstant;
import com.cy.share.common.constant.ResultCode;
import com.cy.share.common.utils.JwtResult;
import com.cy.share.common.utils.JwtUtil;
import com.cy.share.common.utils.RsaKeyHolder;
import com.cy.share.common.utils.Result;
import com.cy.share.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;

@Slf4j
@RequiredArgsConstructor
public class MyInterceptor implements HandlerInterceptor {

    private final RsaKeyHolder rsaKeyHolder;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) return true;

        Method method = handlerMethod.getMethod();
        if (method.getAnnotation(UnInterception.class) != null) return true;

        String authHeader = request.getHeader("Authorization");
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            print(response, Result.fail(ResultCode.UNAUTHORIZED, "无权限，请先登录"), 401);
            return false;
        }
        String token = authHeader.substring(7);

        JwtResult checkResult = JwtUtil.validateAccessToken(token, rsaKeyHolder.getPublicKey());
        if (checkResult.isSuccess()) {
            UserContext.set(checkResult.getClaims().getSubject());
            return true;
        }

        switch (checkResult.getErrCode()) {
            case Jwtconstant.JWT_ERRCODE_EXPIRE -> {
                print(response, Result.fail(ResultCode.UNAUTHORIZED, "token 已过期"), 4001);
                return false;
            }
            default -> {
                print(response, Result.fail(ResultCode.UNAUTHORIZED, "token 校验不通过"), 401);
                return false;
            }
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.remove();
        if (ex != null) log.error("请求处理过程中发生异常", ex);
    }

    private void print(HttpServletResponse response, Object result, int httpStatus) {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(httpStatus);
        try (PrintWriter writer = response.getWriter()) {
            writer.println(result);
            writer.flush();
        } catch (IOException e) {
            log.error("向响应流写入数据失败", e);
        }
    }
}
