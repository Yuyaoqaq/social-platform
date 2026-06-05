package com.cy.share.common.handler;

import com.cy.share.common.exception.*;
import com.cy.share.common.utils.Result;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserRegistException.class)
    public Result handleUserRegistException(UserRegistException e) {
        return Result.fail(e.getCode(), e.getMsg());
    }

    @ExceptionHandler(CodeSendException.class)
    public Result handleCodeSendException(CodeSendException e) {
        return Result.fail(e.getCode(), e.getMsg());
    }

    @ExceptionHandler(CodeWrongException.class)
    public Result handleCodeWrongException(CodeWrongException e) {
        return Result.fail(e.getCode(), e.getMsg());
    }

    @ExceptionHandler(UserSaveException.class)
    public Result handleUserSaveException(UserSaveException e) {
        return Result.fail(e.getCode(), e.getMsg());
    }

    @ExceptionHandler(LogSaveException.class)
    public Result handleLogSaveException(LogSaveException e) {
        return Result.fail(e.getCode(), e.getMsg());
    }

    @ExceptionHandler(LogGetException.class)
    public Result handleLogGetException(LogGetException e) {
        return Result.fail(e.getCode(), e.getMsg());
    }

    @ExceptionHandler(LogUpdateException.class)
    public Result handleLogUpdateException(LogUpdateException e) {
        return Result.fail(e.getCode(), e.getMsg());
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(UserNotFoundException.class)
    public Result handleUserNotFoundException(UserNotFoundException e) {
        return Result.fail(e.getCode(), e.getMsg());
    }

    @ExceptionHandler(RuntimeException.class)
    public Result handleRuntimeException(RuntimeException e) {
        return Result.fail(500, "系统异常，请稍后重试");
    }
}
