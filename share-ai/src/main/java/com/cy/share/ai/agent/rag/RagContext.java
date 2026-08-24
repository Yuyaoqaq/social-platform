package com.cy.share.ai.agent.rag;

import com.cy.share.ai.agent.model.AgentSource;

import java.util.List;

/**
 * RAG 检索结果上下文。平台规则单独保存，方便放到动态历史和检索结果之前。
 */
public record RagContext(String platformRules,
                         String augmentedQuery,
                         List<AgentSource> sources,
                         boolean empty,
                         String warning) {
}
