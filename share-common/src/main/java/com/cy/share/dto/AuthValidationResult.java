package com.cy.share.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthValidationResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean valid;
    private String userId;
    private String message;

    public static AuthValidationResult success(String userId) {
        return new AuthValidationResult(true, userId, null);
    }

    public static AuthValidationResult fail(String message) {
        return new AuthValidationResult(false, null, message);
    }
}
