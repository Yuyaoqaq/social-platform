package com.cy.share.common.exception;

import com.cy.share.common.constant.ResultCode;
import lombok.Getter;

@Getter
public class CodeSendException extends RuntimeException{
    private Integer code;
    private String msg;
    public CodeSendException() {
        super("验证码发送失败");
        this.code = ResultCode.INTERNAL_SERVER_ERROR.getCode();
        this.msg = "验证码发送失败";
    }
}
