package com.cy.share.ai.agent.rag;

import com.cy.share.ai.agent.config.AgentProperties;
import com.cy.share.ai.agent.model.AgentSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按资料库分别执行混合检索，再把平台规则固定加入上下文。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagRetrievalService {

    private static final int CONTENT_LIMIT = 3;
    private static final int TITLE_LIMIT = 3;
    private static final int STYLE_LIMIT = 1;
    private static final int IMAGE_LIMIT = 2;

    private final QdrantKnowledgeStore knowledgeStore;
    private final ContextualQueryAugmenter queryAugmenter;
    private final AgentProperties properties;

    public RagContext retrieve(String userQuery) {
        List<KnowledgeMatch> matches = new ArrayList<>();
        Map<String, Integer> libraryLimits = new LinkedHashMap<>();
        libraryLimits.put("content_materials", CONTENT_LIMIT);
        libraryLimits.put("title_materials", TITLE_LIMIT);
        libraryLimits.put("style_templates", STYLE_LIMIT);
        libraryLimits.put("image_materials", IMAGE_LIMIT);
        knowledgeStore.search(userQuery, libraryLimits).values().forEach(matches::addAll);

        Map<String, KnowledgeMatch> uniqueMatches = new LinkedHashMap<>();
        matches.forEach(match -> uniqueMatches.putIfAbsent(match.id(), match));
        matches = new ArrayList<>(uniqueMatches.values());

        List<Document> documents = new ArrayList<>(matches.stream()
                .map(match -> new Document(match.id(), match.content(), match.metadata()))
                .toList());
        String platformRules = loadPlatformRules();
        String augmentedQuery = documents.isEmpty()
                ? ""
                : queryAugmenter.augment(new Query(userQuery), documents).text();
        List<AgentSource> sources = matches.stream()
                .map(match -> new AgentSource(
                        "knowledge",
                        displayTitle(match),
                        null,
                        String.valueOf(match.metadata().getOrDefault("library", "knowledge"))))
                .toList();
        boolean empty = matches.isEmpty() && platformRules.isBlank();
        return new RagContext(
                platformRules,
                augmentedQuery,
                sources,
                empty,
                empty ? "本次未检索到知识库资料" : null);
    }

    private String displayTitle(KnowledgeMatch match) {
        for (String line : match.content().split("\n")) {
            String value = line.replaceFirst("^[#*\\-\\s]+", "").trim();
            if (!value.isEmpty()) {
                return value.length() <= 60 ? value : value.substring(0, 60) + "...";
            }
        }
        return String.valueOf(match.metadata().getOrDefault("category", "知识库资料"));
    }

    private String loadPlatformRules() {
        Path root = resolveKnowledgeRoot();
        Path source = root.resolve("platform_rules").resolve("source.md");
        if (!Files.isRegularFile(source)) {
            return "";
        }
        try {
            return Files.readString(source, StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            log.warn("读取平台规则失败: {}", source.toAbsolutePath(), e);
            return "";
        }
    }

    private Path resolveKnowledgeRoot() {
        Path configured = Path.of(properties.getRag().getKnowledgePath()).normalize();
        if (Files.isDirectory(configured)) {
            return configured;
        }
        Path rootCandidate = Path.of("knowledge").normalize();
        return Files.isDirectory(rootCandidate) ? rootCandidate : configured;
    }

}
