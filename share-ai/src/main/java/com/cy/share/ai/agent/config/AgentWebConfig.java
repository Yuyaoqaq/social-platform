package com.cy.share.ai.agent.config;

import com.cy.share.ai.agent.auth.AgentAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Agent Web 层配置，将认证拦截器注册到 /agent/** 路径，确保所有 Agent 接口请求经过登录校验。
 */
@Configuration
@RequiredArgsConstructor
public class AgentWebConfig implements WebMvcConfigurer {

    private final AgentAuthInterceptor agentAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(agentAuthInterceptor)
                .addPathPatterns("/agent/**");
    }
}
