package com.cy.share.ai.agent.rag;

import java.util.Map;

/**
 * 知识分块记录(点)，表示一篇知识文档切分后的单个文本块，包含 ID、内容和元数据。
 */
public record KnowledgeChunk(String id, String content, Map<String, Object> metadata) {
}
