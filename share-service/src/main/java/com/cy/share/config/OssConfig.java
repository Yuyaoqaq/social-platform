package com.cy.share.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.profile.DefaultProfile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云OSS手动配置（替代原aliyun-oss-spring-boot-starter自动配置）
 */
@Configuration
public class OssConfig {

    @Value("${alibaba.cloud.access-key}")
    private String accessKey;

    @Value("${alibaba.cloud.secret-key}")
    private String secretKey;

    @Value("${alibaba.cloud.oss.endpoint}")
    private String endpoint;

    @Bean
    public OSS ossClient() {
        return new OSSClientBuilder().build(endpoint, accessKey, secretKey);
    }

    @Bean
    public IAcsClient stsClient() {
        DefaultProfile profile = DefaultProfile.getProfile("cn-beijing", accessKey, secretKey);
        return new DefaultAcsClient(profile);
    }
}
