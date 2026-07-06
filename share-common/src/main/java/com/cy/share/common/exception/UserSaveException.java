package com.cy.share.common.exception;

import lombok.Getter;

@Getter
public class UserSaveException extends RuntimeException {
    private Integer code;
    private String msg;
    public UserSaveException() {
        super("用户注册添加失败");
        this.code = 5003;
        this.msg = "用户注册添加失败";
    }
}
