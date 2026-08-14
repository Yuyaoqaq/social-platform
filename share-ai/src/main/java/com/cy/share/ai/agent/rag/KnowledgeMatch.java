package com.cy.share.ai.agent.rag;

import java.util.Map;

/**
 * Qdrant 向量检索命中记录，包含匹配到的知识块 ID、内容、相似度分数和元数据。（比点多一个分数字段）
 */
public record KnowledgeMatch(String id, String content, double score, Map<String, Object> metadata) {
}
