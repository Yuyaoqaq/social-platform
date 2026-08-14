package com.cy.share.ai.agent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具调用记录 DTO，记录每次工具调用的名称、参数、执行状态和结果摘要，最终返回给前端展示。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentToolCallRecord {
    private String toolName;
    private String arguments;
    private String status;
    private String resultSummary;
}
