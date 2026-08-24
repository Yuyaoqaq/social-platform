package com.cy.share.ai.agent.rag;

import com.cy.share.ai.agent.config.AgentProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Qdrant 知识库存储。
 * 每个知识点同时保存语义向量和 BM25 关键词向量，查询时通过 RRF 合并两路排名。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QdrantKnowledgeStore {

    private static final int MIN_VECTOR_SIZE = 256;
    private static final int MAX_VECTOR_SIZE = 2560;
    private static final int UPSERT_BATCH_SIZE = 64;
    private static final String DENSE_VECTOR = "dense";
    private static final String BM25_VECTOR = "bm25";
    private static final String BM25_MODEL = "qdrant/bm25";

    private final AgentProperties properties;
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    /**
     * 全量重建知识集合，避免已被删除或改名的旧知识继续留在检索结果中。
     */
    public int replaceAll(List<KnowledgeChunk> chunks) {
        if (!properties.getRag().isEnabled() || chunks.isEmpty()) {
            return 0;
        }

        EmbeddingModel embeddingModel = requireEmbeddingModel();
        int vectorSize = configuredVectorSize();
        List<Map<String, Object>> points = new ArrayList<>(chunks.size());

        // 先完成语义向量生成，再替换旧集合，降低重建中途失败的影响。
        for (KnowledgeChunk chunk : chunks) {
            //得到每块知识的metadata中的（分类 ： 内容）
            String indexText = indexText(chunk);
            float[] denseVector = embeddingModel.embed(indexText);
            validateVectorDimensions(denseVector.length, vectorSize);
            //拼装payload
            Map<String, Object> payload = new HashMap<>(chunk.metadata());
            payload.put("content", chunk.content());
            points.add(Map.of(
                    "id", chunk.id(),
                    "vector", Map.of(
                            DENSE_VECTOR, denseVector,
                            BM25_VECTOR, bm25Document(indexText)),
                    "payload", payload));
        }

        RestClient client = restClientBuilder.build();
        recreateCollection(client, vectorSize);
        String url = collectionUrl() + "/points?wait=true";
        for (int start = 0; start < points.size(); start += UPSERT_BATCH_SIZE) {
            int end = Math.min(points.size(), start + UPSERT_BATCH_SIZE);
            client.put().uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("points", points.subList(start, end)))
                    .retrieve()
                    .toBodilessEntity();
        }
        return points.size();
    }
    //《库名 ： 检索结果列表》
    public Map<String, List<KnowledgeMatch>> search(String query,
                                                     Map<String, Integer> libraryLimits) {
        Map<String, List<KnowledgeMatch>> results = new LinkedHashMap<>();
        libraryLimits.keySet().forEach(library -> results.put(library, List.of()));
        if (!properties.getRag().isEnabled() || query == null || query.isBlank()
                || libraryLimits.isEmpty()) {
            return results;
        }
        EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
        if (embeddingModel == null) {
            log.warn("EmbeddingModel is unavailable, skip Qdrant retrieval");
            return results;
        }

        try {
            float[] denseVector = embeddingModel.embed(query);
            validateVectorDimensions(denseVector.length, configuredVectorSize());
            //针对每一个库进行混合检索，降级稠密相似度检索
            for (Map.Entry<String, Integer> request : libraryLimits.entrySet()) {
                String library = request.getKey();
                int limit = request.getValue();
                try {
                    results.put(library, hybridSearch(query, denseVector, library, limit));
                } catch (Exception hybridError) {
                    log.warn("Qdrant hybrid retrieval failed, fallback to dense retrieval. library={}",
                            library, hybridError);
                    try {
                        results.put(library, denseSearch(denseVector, library, limit));
                    } catch (Exception denseError) {
                        log.warn("Qdrant dense fallback failed. library={}", library, denseError);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Qdrant retrieval failed, continue without RAG. query={}", query, e);
        }
        return results;
    }

    //混合检索，先用稠密向量和 BM25 关键词向量各自检索候选，再用加权 RRF 排名融合（语义 70%、关键词 30%）。
    private List<KnowledgeMatch> hybridSearch(String query,
                                               float[] denseVector,
                                               String library,
                                               int limit) throws Exception {
        //从Qdrant底层捞出来的“候选者”数
        int candidateLimit = Math.max(limit, properties.getRag().getHybridCandidateK());
        //给Qdrant看的语句，保证只在指定库中检索
        Map<String, Object> filter = libraryFilter(library);

        Map<String, Object> densePrefetch = new HashMap<>();
        densePrefetch.put("query", denseVector);
        densePrefetch.put("using", DENSE_VECTOR);
        densePrefetch.put("limit", candidateLimit);
        addFilter(densePrefetch, filter);

        Map<String, Object> keywordPrefetch = new HashMap<>();
        keywordPrefetch.put("query", bm25Document(query));
        keywordPrefetch.put("using", BM25_VECTOR);
        keywordPrefetch.put("limit", candidateLimit);
        addFilter(keywordPrefetch, filter);

        Map<String, Object> body = new HashMap<>();
        body.put("prefetch", List.of(densePrefetch, keywordPrefetch));
        // 加权 RRF：语义相似度占 70%、BM25 关键词占 30%（需要 Qdrant >= 1.17.0）
        body.put("query", Map.of(
                "fusion", "rrf",
                "rrf", Map.of("weights", List.of(0.7f, 0.3f))));
        body.put("limit", Math.max(1, limit));
        body.put("with_payload", true);

        String response = restClientBuilder.build().post()
                .uri(collectionUrl() + "/points/query")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
        return parseMatches(response);
    }

    private List<KnowledgeMatch> denseSearch(float[] denseVector,
                                             String library,
                                             int limit) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("query", denseVector);
        body.put("using", DENSE_VECTOR);
        body.put("limit", Math.max(1, limit));
        body.put("with_payload", true);
        Map<String, Object> filter = libraryFilter(library);
        if (!filter.isEmpty()) {
            body.put("filter", filter);
        }

        String response = restClientBuilder.build().post()
                .uri(collectionUrl() + "/points/query")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
        return parseMatches(response);
    }

    private List<KnowledgeMatch> parseMatches(String body) throws Exception {
        JsonNode result = objectMapper.readTree(body).path("result");
        JsonNode points = result.isArray() ? result : result.path("points");
        List<KnowledgeMatch> matches = new ArrayList<>();

        for (JsonNode item : points) {
            Map<String, Object> metadata = objectMapper.convertValue(
                    item.path("payload"), new TypeReference<Map<String, Object>>() { });
            Object contentValue = metadata.remove("content");
            String content = contentValue == null ? "" : String.valueOf(contentValue);
            matches.add(new KnowledgeMatch(
                    item.path("id").asText(),
                    content,
                    item.path("score").asDouble(),
                    metadata));
        }
        return matches;
    }

    private String indexText(KnowledgeChunk chunk) {
        String category = String.valueOf(chunk.metadata().getOrDefault("category", "")).trim();
        return category.isEmpty() ? chunk.content() : category + "\n" + chunk.content();
    }

    private Map<String, Object> bm25Document(String text) {
        return Map.of(
                "text", text,
                "model", BM25_MODEL,
                "options", Map.of(
                        "tokenizer", "multilingual",
                        "stemmer", Map.of("type", "none"),
                        "stopwords", Map.of()));
    }

    private Map<String, Object> libraryFilter(String library) {
        if (library == null || library.isBlank()) {
            return Map.of();
        }
        return Map.of("must", List.of(Map.of(
                "key", "library",
                "match", Map.of("value", library))));
    }

    private void addFilter(Map<String, Object> request, Map<String, Object> filter) {
        if (!filter.isEmpty()) {
            request.put("filter", filter);
        }
    }

    private void recreateCollection(RestClient client, int dimensions) {
        try {
            client.delete().uri(collectionUrl()).retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() != 404) {
                throw e;
            }
        }

        client.put().uri(collectionUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "vectors", Map.of(DENSE_VECTOR, Map.of(
                                "size", dimensions,
                                "distance", configuredDistance())),
                        "sparse_vectors", Map.of(BM25_VECTOR, Map.of(
                                "modifier", "idf"))))
                .retrieve()
                .toBodilessEntity();
    }

    private int configuredVectorSize() {
        int vectorSize = properties.getRag().getVectorSize();
        if (vectorSize < MIN_VECTOR_SIZE || vectorSize > MAX_VECTOR_SIZE) {
            throw new IllegalArgumentException("RAG 向量维度必须在 256~2560 之间，当前配置为 " + vectorSize);
        }
        return vectorSize;
    }

    private String configuredDistance() {
        String distance = properties.getRag().getDistance();
        if (distance == null || distance.isBlank()) {
            throw new IllegalArgumentException("RAG 相似度算法不能为空");
        }
        return distance;
    }

    private void validateVectorDimensions(int actualDimensions, int expectedDimensions) {
        if (actualDimensions != expectedDimensions) {
            throw new IllegalStateException("Embedding 输出维度和 RAG 配置不一致，实际 "
                    + actualDimensions + "，配置 " + expectedDimensions);
        }
    }

    private EmbeddingModel requireEmbeddingModel() {
        EmbeddingModel model = embeddingModelProvider.getIfAvailable();
        if (model == null) {
            throw new IllegalStateException("EmbeddingModel 未配置，无法建立知识库索引");
        }
        return model;
    }

    private String collectionUrl() {
        return baseUrl() + "/collections/" + properties.getRag().getCollection();
    }

    private String baseUrl() {
        return properties.getRag().getQdrantUrl().replaceAll("/+$", "");
    }
}
