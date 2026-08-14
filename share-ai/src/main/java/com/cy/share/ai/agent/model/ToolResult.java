package com.cy.share.ai.agent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具执行结果泛型包装，统一所有 Agent 工具的返回格式，包含成功/失败状态、错误码、是否可重试及业务数据。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult<T> {

    private boolean success;
    private String code;
    private String message;
    private boolean retryable;
    private T data;

    public static <T> ToolResult<T> success(T data) {
        return new ToolResult<>(true, "OK", null, false, data);
    }

    public static <T> ToolResult<T> fail(String code, String message, boolean retryable) {
        return new ToolResult<>(false, code, message, retryable, null);
    }
}
