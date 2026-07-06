package com.cy.share.common.exception;


import lombok.Getter;

@Getter
public class LogSaveException extends RuntimeException{
    private Integer code;
    private String msg;
    public LogSaveException() {
        super("博客添加失败");
        this.code = 5002;
        this.msg = "博客添加失败";
    }
}
