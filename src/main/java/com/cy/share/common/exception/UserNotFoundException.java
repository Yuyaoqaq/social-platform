package com.cy.share.common.exception;

import com.cy.share.common.constant.ResultCode;
import lombok.Getter;

@Getter
public class UserNotFoundException extends RuntimeException {
    private final Integer code;
    private final String msg;

    public UserNotFoundException() {
        super("用户不存在或密码错误");
        this.code = ResultCode.DATA_NOT_FOUND.getCode();
        this.msg = "用户不存在或密码错误";
    }
}
