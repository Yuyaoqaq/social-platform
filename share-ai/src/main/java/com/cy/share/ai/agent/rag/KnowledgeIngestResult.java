package com.cy.share.ai.agent.rag;

/**
 * 知识入库结果，记录本次索引操作处理的文件数和切分后的块数。
 */
public record KnowledgeIngestResult(int files, int chunks) {
}
