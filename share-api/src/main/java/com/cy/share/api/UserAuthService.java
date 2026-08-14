package com.cy.share.api;

import com.cy.share.dto.AuthValidationResult;

public interface UserAuthService {

    AuthValidationResult validateAccessToken(String token);
}
