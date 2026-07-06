package com.cy.share.common.exception;

import com.cy.share.common.constant.ResultCode;
import lombok.Getter;

@Getter
public class CodeWrongException extends RuntimeException {
    private Integer code;
    private String msg;
    public CodeWrongException() {
        super("验证码错误");
        this.code = 5001;
        this.msg = "验证码错误";
    }
}
