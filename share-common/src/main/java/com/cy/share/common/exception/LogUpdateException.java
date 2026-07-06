package com.cy.share.common.exception;

import lombok.Getter;

@Getter
public class LogUpdateException extends RuntimeException{
    private Integer code;
    private String msg;
    public LogUpdateException() {
        super("博客更新失败");
        this.code = 5005;
        this.msg = "博客更新失败";
    }
}
