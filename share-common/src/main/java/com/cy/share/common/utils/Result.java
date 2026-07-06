package com.cy.share.common.utils;
import com.cy.share.common.constant.ResultCode;
import lombok.Data;

/**
 * 统一返回结果封装
 * @param <T> 响应数据的类型（支持泛型，灵活适配各种数据结构）
 */
@Data
public class Result<T> {
    // 状态码（对应 ResultCode 中的 code）
    private int code;
    // 提示信息（对应 ResultCode 中的 msg，或自定义信息）
    private String msg;
    // 业务数据（成功时返回，失败时可null）
    private T data;

    private Long total;

    // 私有构造器，避免直接实例化，通过静态方法创建
    private Result() {}

    /**
     * 成功响应（带数据）
     */
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.code = ResultCode.SUCCESS.getCode();
        result.msg = ResultCode.SUCCESS.getMsg();
        result.data = data;
        return result;
    }
    //分页重载
    public static <T> Result<T> success(T data,Long total) {
        Result<T> result = new Result<>();
        result.code = ResultCode.SUCCESS.getCode();
        result.msg = ResultCode.SUCCESS.getMsg();
        result.data = data;
        result.total = total;
        return result;
    }
    //状态码+信息重载
    public static <T> Result<T> success(Integer code,T data) {
        Result<T> result = new Result<>();
        result.code = code;
        result.msg = ResultCode.SUCCESS.getMsg();
        result.data = data;
        return result;
    }

    /**
     * 成功响应（不带数据，仅返回成功状态）
     */
    public static <T> Result<T> success() {
        return success(null);
    }

    /**
     * 失败响应（基于 ResultCode 枚举）
     */
    public static <T> Result<T> fail(ResultCode code) {
        Result<T> result = new Result<>();
        result.code = code.getCode();
        result.msg = code.getMsg();
        result.data = null;
        return result;
    }

    /**
     * 失败响应（自定义提示信息）
     */
    public static <T> Result<T> fail(ResultCode code, String customMsg) {
        Result<T> result = new Result<>();
        result.code = code.getCode();
        result.msg = customMsg; // 优先使用自定义信息
        result.data = null;
        return result;
    }

    /**
     * 失败响应（直接传入状态码和信息，用于特殊场景）
     */
    public static <T> Result<T> fail(int code, String msg) {
        Result<T> result = new Result<>();
        result.code = code;
        result.msg = msg;
        result.data = null;
        return result;
    }
}