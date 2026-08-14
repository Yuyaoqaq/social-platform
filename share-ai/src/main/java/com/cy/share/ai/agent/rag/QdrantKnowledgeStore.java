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
import java.util.List;
import java.util.Map;

/**
 * Qdrant 向量数据库客户端，负责向量嵌入、集合管理、向量 upsert 和相似度搜索，
 * 是 RAG 知识库的底层存储与检索引擎。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QdrantKnowledgeStore {
    //规定维度
    private static final int MIN_VECTOR_SIZE = 256;
    private static final int MAX_VECTOR_SIZE = 2560;

    private final AgentProperties properties;
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    //添加或更新（操作点集合）
    public int upsert(List<KnowledgeChunk> chunks) {
        if (!properties.getRag().isEnabled() || chunks.isEmpty()) {
            return 0;
        }
        EmbeddingModel embeddingModel = requireEmbeddingModel();
        RestClient client = restClientBuilder.build();
        List<Map<String, Object>> points = new ArrayList<>();
        //[
        //  {
        //    "id": "chunk-123",
        //    "vector": [0.1, 0.2, ...], // float array
        //    "payload": {
        //      "content": "实际文本...",
        //      "chunkId": "chunk-123",
        //      "source": "doc.pdf" // metadata 中的其他键
        //    }
        //  },
        //]
        int vectorSize = configuredVectorSize();

        for (KnowledgeChunk chunk : chunks) {
            float[] vector = embeddingModel.embed(chunk.content());
            validateVectorDimensions(vector.length, vectorSize);
            Map<String, Object> payload = new HashMap<>(chunk.metadata());
            payload.put("content", chunk.content());
            payload.put("chunkId", chunk.id());
            points.add(Map.of(
                    "id", chunk.id(),
                    "vector", vector,
                    "payload", payload));
        }

        ensureCollection(client, vectorSize);
        //同步等待
        String url = baseUrl() + "/collections/" + properties.getRag().getCollection() + "/points?wait=true";
        client.put().uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("points", points))
                .retrieve()
                //用于忽略响应体（Body），只返回一个 ResponseEntity 对象（包含状态码、头部等信息）
                .toBodilessEntity();
        return points.size();
    }

    //搜索
    public List<KnowledgeMatch> search(String query, int limit) {
        if (!properties.getRag().isEnabled()) {
            return List.of();
        }
        EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
        if (embeddingModel == null) {
            log.warn("EmbeddingModel is unavailable, skip Qdrant retrieval");
            return List.of();
        }

        try {
            float[] vector = embeddingModel.embed(query);
            validateVectorDimensions(vector.length, configuredVectorSize());
            String url = baseUrl() + "/collections/" + properties.getRag().getCollection() + "/points/search";
            String body = restClientBuilder.build().post().uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "vector", vector,
                            "limit", Math.max(1, limit),
                            //是否返回payload元数据
                            "with_payload", true))
                    .retrieve()
                    .body(String.class);

            JsonNode result = objectMapper.readTree(body).path("result");
            List<KnowledgeMatch> matches = new ArrayList<>();
            for (JsonNode item : result) {
                JsonNode payloadNode = item.path("payload");
                Map<String, Object> metadata = objectMapper.convertValue(
                        payloadNode, new TypeReference<Map<String, Object>>() { });
                String content = String.valueOf(metadata.remove("content"));
                matches.add(new KnowledgeMatch(
                        item.path("id").asText(),
                        content,
                        item.path("score").asDouble(),
                        metadata));
            }
            return matches;
        } catch (Exception e) {
            log.warn("Qdrant retrieval failed, continue without RAG. query={}", query, e);
            return List.of();
        }
    }
    //确保集合存在并且配置正确
    private void ensureCollection(RestClient client, int dimensions) {
        String collectionUrl = baseUrl() + "/collections/" + properties.getRag().getCollection();
        try {
            String body = client.get().uri(collectionUrl).retrieve().body(String.class);
            validateCollectionConfig(body, dimensions);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() != 404) {
                throw e;
            }
            client.put().uri(collectionUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("vectors", Map.of(
                            "size", dimensions,
                            "distance", configuredDistance())))
                    .retrieve()
                    .toBodilessEntity();
        }
    }

    //校验向量化后数据维度是否在范围内
    private int configuredVectorSize() {
        int vectorSize = properties.getRag().getVectorSize();
        if (vectorSize < MIN_VECTOR_SIZE || vectorSize > MAX_VECTOR_SIZE) {
            throw new IllegalArgumentException("RAG 向量维度必须在 256~2560 之间，当前配置为 " + vectorSize);
        }
        return vectorSize;
    }

    //获取 RAG 配置的相似度算法
    private String configuredDistance() {
        String distance = properties.getRag().getDistance();
        if (distance == null || distance.isBlank()) {
            throw new IllegalArgumentException("RAG 相似度算法不能为空");
        }
        return distance;
    }

    //验证向量维度是否与 RAG 配置一致
    private void validateVectorDimensions(int actualDimensions, int expectedDimensions) {
        if (actualDimensions != expectedDimensions) {
            throw new IllegalStateException("Embedding 输出维度和 RAG 配置不一致，实际 "
                    + actualDimensions + "，配置 " + expectedDimensions);
        }
    }

    //验证集合中相似度搜索算法和维度配置是否一致
    private void validateCollectionConfig(String body, int dimensions) {
        try {
            JsonNode vectors = objectMapper.readTree(body).path("result").path("config").path("params").path("vectors");
            int actualSize = vectors.path("size").asInt(dimensions);
            String actualDistance = vectors.path("distance").asText(configuredDistance());
            if (actualSize != dimensions || !actualDistance.equalsIgnoreCase(configuredDistance())) {
                throw new IllegalStateException("Qdrant 集合配置和当前 RAG 配置不一致，集合维度="
                        + actualSize + "，集合算法=" + actualDistance
                        + "，当前维度=" + dimensions + "，当前算法=" + configuredDistance());
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Qdrant collection config check failed, skip strict check", e);
        }
    }

    //获取 Embedding 模型
    private EmbeddingModel requireEmbeddingModel() {
        EmbeddingModel model = embeddingModelProvider.getIfAvailable();
        if (model == null) {
            throw new IllegalStateException("EmbeddingModel 未配置，无法建立知识库索引");
        }
        return model;
    }

    //去掉末尾的斜杠
    private String baseUrl() {
        return properties.getRag().getQdrantUrl().replaceAll("/+$", "");
    }
}
