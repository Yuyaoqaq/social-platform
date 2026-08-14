package com.cy.share.service.rpc;

import com.cy.share.api.UserAuthService;
import com.cy.share.common.constant.Jwtconstant;
import com.cy.share.common.utils.JwtResult;
import com.cy.share.common.utils.JwtUtil;
import com.cy.share.common.utils.RsaKeyHolder;
import com.cy.share.dto.AuthValidationResult;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService
@RequiredArgsConstructor
public class UserAuthServiceImpl implements UserAuthService {

    private final RsaKeyHolder rsaKeyHolder;

    @Override
    public AuthValidationResult validateAccessToken(String token) {
        if (token == null || token.isBlank()) {
            return AuthValidationResult.fail("请先登录");
        }

        JwtResult result = JwtUtil.validateAccessToken(token, rsaKeyHolder.getPublicKey());
        if (result.isSuccess()) {
            return AuthValidationResult.success(result.getClaims().getSubject());
        }
        if (result.getErrCode() == Jwtconstant.JWT_ERRCODE_EXPIRE) {
            return AuthValidationResult.fail("token 已过期");
        }
        return AuthValidationResult.fail("token 校验不通过");
    }
}
