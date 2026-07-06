package com.cy.share.common.exception;

import com.cy.share.common.constant.ResultCode;
import lombok.AllArgsConstructor;
import lombok.Getter;


@Getter
public class UserRegistException extends RuntimeException{
    private Integer code;
    private String msg;
    public UserRegistException() {
        super("注册用户的手机号已存在");
        this.code = ResultCode.DATA_DUPLICATE.getCode();
        this.msg = "注册用户的手机号已存在";
    }
}
