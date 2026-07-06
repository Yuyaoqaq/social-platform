package com.cy.share.common.constant;
/**
 * 响应状态码枚举
 */
public enum ResultCode {
    // 成功状态
    SUCCESS(200, "操作成功"),

    // 客户端错误（4xx）
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权（请登录）"),
    FORBIDDEN(403, "权限不足，拒绝访问"),
    NOT_FOUND(404, "请求资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),

    // 服务器错误（5xx）
    INTERNAL_SERVER_ERROR(500, "服务器内部错误"),
    SERVICE_UNAVAILABLE(503, "服务暂时不可用"),

    // 业务逻辑错误（可根据业务扩展）
    BUSINESS_ERROR(600, "业务逻辑错误"),
    LIKE_ALREADY_LIKED(601, "已经点过赞了"),
    LIKE_NOT_LIKED(602, "尚未点赞，无法取消"),
    DATA_DUPLICATE(603, "数据已存在"),
    DATA_NOT_FOUND(604, "用户不存在"),

    //数据库错误
    DATA_HANDLE_ERROR(700,"数据库错误");

    // 状态码
    private final int code;
    // 提示信息
    private final String msg;

    ResultCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    // getter 方法
    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}
