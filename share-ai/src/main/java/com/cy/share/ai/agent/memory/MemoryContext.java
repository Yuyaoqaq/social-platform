package com.cy.share.ai.agent.memory;

import java.util.List;

/**
 * 记忆上下文，包含历史对话增量摘要和最近的对话消息，作为 ReAct 工作流的输入之一。
 */
public record MemoryContext(String summary, List<AiMessage> recentMessages) {
}
