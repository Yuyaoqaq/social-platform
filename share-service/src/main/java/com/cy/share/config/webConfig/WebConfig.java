package com.cy.share.config.webConfig;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") //路径
//                .allowedOrigins("Http://localhost:8080","null") //来源
//                .allowedMethods("GET","POST","PUT","DELETE","OPTIONS") //方法
//                .allowCredentials(true) //允许携带token类
//                .maxAge(3600); //预检请求缓存时间
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE")
//                .allowedHeaders("x-user-role", "Content-Type") // 必须显式允许
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600); //预检请求缓存时间

    }
}
