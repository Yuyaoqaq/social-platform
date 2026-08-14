package com.cy.share.ai.agent.rag;

import com.cy.share.ai.agent.config.AgentProperties;
import com.cy.share.ai.agent.model.AgentSource;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 检索增强生成服务
 * 按需补充图片素材检索，
 * 最终返回 RagContext 供工作流注入。
 */
@Service
@RequiredArgsConstructor
public class RagRetrievalService {

    private final QdrantKnowledgeStore knowledgeStore;
    private final ContextualQueryAugmenter queryAugmenter;
    private final AgentProperties properties;

    public RagContext retrieve(String userQuery) {
        List<KnowledgeMatch> matches = new ArrayList<>(
                knowledgeStore.search(userQuery, properties.getRag().getTopK()));
        if (needsImageMaterial(userQuery)) {
            matches.addAll(knowledgeStore.search(
                    userQuery + " 已授权图片素材 图片地址 URL 适合用途", 3));
        }
        //《id ---> KnowledgeMatch》映射
        Map<String, KnowledgeMatch> uniqueMatches = new LinkedHashMap<>();//双向链表
        matches.forEach(match -> uniqueMatches.putIfAbsent(match.id(), match));
        matches = new ArrayList<>(uniqueMatches.values());
        List<Document> documents = matches.stream()
                .map(match -> new Document(match.id(), match.content(), match.metadata()))
                .toList();
        Query augmented = queryAugmenter.augment(new Query(userQuery), documents);
        List<AgentSource> sources = matches.stream()
                .map(match -> new AgentSource(
                        "knowledge",
                        String.valueOf(match.metadata().getOrDefault("title", match.metadata().get("source"))),
                        null,
                        String.valueOf(match.metadata().getOrDefault("source", "knowledge"))))
                .toList();
        boolean empty = matches.isEmpty();
        return new RagContext(
                augmented.text(),
                sources,
                empty,
                empty ? "本次未检索到知识库资料" : null);
    }

    //提问是否需要图片素材
    private boolean needsImageMaterial(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        return query.contains("图")
                || query.contains("帖子")
                || query.contains("草稿")
                || query.contains("创作")
                || query.contains("发布")
                || query.contains("文案");
    }
}
