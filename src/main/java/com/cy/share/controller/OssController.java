package com.cy.share.controller;

import com.aliyuncs.IAcsClient;
import com.aliyuncs.sts.model.v20150401.AssumeRoleRequest;
import com.aliyuncs.sts.model.v20150401.AssumeRoleResponse;
import com.cy.share.common.annotation.UnInterception;
import com.cy.share.common.constant.ResultCode;
import com.cy.share.common.utils.JwtResult;
import com.cy.share.common.utils.JwtUtil;
import com.cy.share.common.utils.Result;
import com.cy.share.common.utils.RsaKeyHolder;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/oss")
@RequiredArgsConstructor
public class OssController {

    @Resource
    IAcsClient stsClient;

    private final RsaKeyHolder rsaKeyHolder;

    @Value("${alibaba.cloud.oss.endpoint}")
    private String endpoint;

    @Value("${alibaba.cloud.oss.bucket}")
    private String bucket;

    @Value("${alibaba.cloud.sts.role-arn}")
    private String roleArn;

    @GetMapping("/sts")
    @UnInterception
    public Result sts(HttpServletRequest request, @RequestParam(name = "phone", required = false) String phone) {
        String authHeader = request.getHeader("Authorization");
        String token = (authHeader != null && authHeader.startsWith("Bearer "))
                ? authHeader.substring(7) : null;
        JwtResult jwtResult = (token != null)
                ? JwtUtil.validateAccessToken(token, rsaKeyHolder.getPublicKey())
                : new JwtResult();

        String dirId;
        if (jwtResult.isSuccess()) {
            dirId = jwtResult.getClaims().getSubject();
        } else if (phone != null && !phone.isEmpty()) {
            dirId = phone;
        } else {
            return Result.fail(ResultCode.UNAUTHORIZED, "请先登录或提供手机号");
        }

        String policy = String.format(
            "{\"Version\":\"1\",\"Statement\":[{\"Effect\":\"Allow\",\"Action\":[\"oss:PutObject\"],\"Resource\":[\"acs:oss:*:*:%s/uploads/%s/*\"]}]}",
            bucket, dirId
        );

        try {
            AssumeRoleRequest assumeRoleReq = new AssumeRoleRequest();
            assumeRoleReq.setRoleArn(roleArn);
            assumeRoleReq.setRoleSessionName("upload-" + dirId);
            assumeRoleReq.setDurationSeconds(3600L);
            assumeRoleReq.setPolicy(policy);

            AssumeRoleResponse assumeRoleResp = stsClient.getAcsResponse(assumeRoleReq);
            AssumeRoleResponse.Credentials credentials = assumeRoleResp.getCredentials();

            Map<String, String> result = new LinkedHashMap<>();
            result.put("accessKeyId", credentials.getAccessKeyId());
            result.put("accessKeySecret", credentials.getAccessKeySecret());
            result.put("securityToken", credentials.getSecurityToken());
            result.put("expiration", credentials.getExpiration());
            result.put("bucket", bucket);
            result.put("endpoint", endpoint);
            result.put("dir", "uploads/" + dirId + "/");

            return Result.success(result);
        } catch (Exception e) {
            return Result.fail(ResultCode.INTERNAL_SERVER_ERROR, "获取STS凭证失败: " + e.getMessage());
        }
    }
}
