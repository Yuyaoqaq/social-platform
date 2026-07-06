package com.cy.share.common.exception;

import lombok.Getter;

@Getter
public class LogGetException extends RuntimeException{
    private Integer code;
    private String msg;
    public LogGetException() {
        super("博客回显查询失败");
        this.code = 5004;
        this.msg = "博客回显查询失败";
    }
}
