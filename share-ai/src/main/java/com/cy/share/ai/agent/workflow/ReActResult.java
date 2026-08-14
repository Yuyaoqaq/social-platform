package com.cy.share.ai.agent.workflow;

import com.cy.share.ai.agent.model.AgentToolCallRecord;
import com.cy.share.ai.agent.model.AgentTokenUsage;

import java.util.List;

/**
 * ReAct 工作流产出，封装模型最终生成的文本、工具调用记录、警告信息和累计 Token 用量。
 */
public record ReActResult(String content,
                          List<AgentToolCallRecord> toolCalls,
                          List<String> warnings,
                          AgentTokenUsage tokenUsage) {
}
