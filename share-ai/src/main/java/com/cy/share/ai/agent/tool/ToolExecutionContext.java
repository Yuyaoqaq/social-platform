package com.cy.share.ai.agent.tool;

/**
 * 工具执行上下文，携带当前用户 ID 及功能开关状态，供各工具在 execute 时判断权限和限额。
 */
public record ToolExecutionContext(
        String userId,
        boolean webSearchEnabled,
        boolean imageGenerationEnabled) {
}
