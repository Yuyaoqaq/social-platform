package com.cy.share.service.rpc;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.cy.share.api.GeneratedImageStorageService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.UUID;

@DubboService
@RequiredArgsConstructor
//生成的图像存储
public class GeneratedImageStorageServiceImpl implements GeneratedImageStorageService {

    private final OSS ossClient;

    @Value("${alibaba.cloud.oss.bucket}")
    private String bucket;

    @Value("${alibaba.cloud.oss.endpoint}")
    private String endpoint;

    @Override
    public String storeFromUrl(String sourceUrl, String userId) {
        // 验证源地址 ---防ssrf
        URI source = validateSourceUrl(sourceUrl);
        // 净化用户 ID
        String safeUserId = sanitizeUserId(userId);
        // 从源地址下载图像数据，读成 byte 数组
        byte[] image = RestClient.create().get()
                .uri(source)
                .retrieve()
                .body(byte[].class);
        if (image == null || image.length == 0) {
            throw new IllegalStateException("生图结果为空，无法转存 OSS");
        }
        // 生成对象名
        String objectName = "uploads/" + safeUserId + "/ai/"
                + UUID.randomUUID() + ".png";
        // 设置元数据
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType("image/png");
        metadata.setContentLength(image.length);
        // 上传到 OSS
        ossClient.putObject(bucket, objectName, new ByteArrayInputStream(image), metadata);
        // 返回 URL
        return "https://" + bucket + "." + endpoint + "/" + objectName;
    }

    //URI是URL的超集，就是少了协议+域名（ip+port）
    private URI validateSourceUrl(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new IllegalArgumentException("生图临时地址为空");
        }
        URI uri = URI.create(sourceUrl);
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || host == null
                || !(host.equals("aliyuncs.com") || host.endsWith(".aliyuncs.com"))) {
            throw new IllegalArgumentException("只允许转存阿里云生图结果地址");
        }
        return uri;
    }

    private String sanitizeUserId(String userId) {
        String safe = userId == null ? "" : userId.replaceAll("[^a-zA-Z0-9_-]", "");
        if (safe.isBlank()) {
            throw new IllegalArgumentException("用户 ID 不能为空");
        }
        return safe;
    }
}
