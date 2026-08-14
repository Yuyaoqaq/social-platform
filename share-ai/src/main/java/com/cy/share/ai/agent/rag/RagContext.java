package com.cy.share.ai.agent.rag;

import com.cy.share.ai.agent.model.AgentSource;

import java.util.List;

/**
 * RAG 检索结果上下文，包含增强后的查询文本、引用来源列表、是否为空和警告信息，供 ReAct 工作流注入 Prompt。
 */
public record RagContext(String augmentedQuery, List<AgentSource> sources, boolean empty, String warning) {
}
