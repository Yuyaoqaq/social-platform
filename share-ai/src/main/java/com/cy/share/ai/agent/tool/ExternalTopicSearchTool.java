package com.cy.share.ai.agent.tool;

import com.cy.share.ai.agent.config.AgentProperties;
import com.cy.share.ai.agent.model.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 外部网页搜索工具，调用 SearchAPI.io 搜索引擎（默认百度）补充站外公开话题素材，结果缓存到 Redis。
 */
@Component
@RequiredArgsConstructor
public class ExternalTopicSearchTool implements AgentTool {

    private final AgentProperties properties;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    //发起HTTP请求
    private final RestClient.Builder restClientBuilder;

    @Override
    public String name() {
        return "search_external_topics";
    }

    @Override
    public String description() {
        return "搜索外部公开网页，补充站外近期热点和主题素材（默认百度）。\n"
                + "什么时候用：用户想了解站外热点、实时话题、公开资讯，或站内检索结果不足需要外部补充时。\n"
                + "什么时候不用：站内能解决的内容优先用 search_content。";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "keyword", Map.of("type", "string", "description", "主题关键词"),
                        "limit", Map.of("type", "integer", "minimum", 1, "maximum", 10)),
                "required", List.of("keyword"));
    }

    @Override
    public ToolResult<?> execute(JsonNode arguments, ToolExecutionContext context) {
        AgentProperties.WebSearch config = properties.getWebSearch();
        if (!config.isEnabled() || !StringUtils.hasText(config.getApiKey())) {
            return ToolResult.fail("WEB_SEARCH_NOT_CONFIGURED", "外部搜索未配置 API Key", false);
        }

        String keyword = arguments.path("keyword").asText("").trim();
        if (keyword.isEmpty()) {
            return ToolResult.fail("INVALID_ARGUMENT", "keyword 不能为空", false);
        }
        String platform = arguments.path("platform").asText("baidu");
        int limit = Math.max(1, Math.min(arguments.path("limit").asInt(5), 10));
        String query = platform + keyword;
        //MD5加密会固定长度
        String cacheKey = "agent:web-search:" + DigestUtils.md5DigestAsHex(
                (query + ":" + limit).getBytes(StandardCharsets.UTF_8));
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (StringUtils.hasText(cached)) {
                return ToolResult.success(objectMapper.readTree(cached));
            }
        } catch (Exception ignored) {
            // 缓存不可用不影响搜索
        }

        try {
            String uri = UriComponentsBuilder.fromHttpUrl(config.getUrl())
                    .queryParam("q", query)
                    .queryParam("api_key", config.getApiKey())
                    .queryParam("engine", config.getEngine())
                    .build()
                    .encode()
                    .toUriString();
            String body = restClientBuilder.build().get().uri(uri).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body);
            JsonNode organicResults = root.path("organic_results");
            List<Map<String, Object>> normalized = new ArrayList<>();
            for (int i = 0; i < Math.min(limit, organicResults.size()); i++) {
                JsonNode item = organicResults.get(i);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("platform", platform);
                result.put("title", item.path("title").asText(""));
                result.put("summary", item.path("snippet").asText(""));
                result.put("url", item.path("link").asText(""));
                result.put("searchRank", item.path("position").asInt(i + 1));
                result.put("heatDataAvailable", false);
                normalized.add(result);
            }
            String cacheValue = objectMapper.writeValueAsString(normalized);
            try {
                redisTemplate.opsForValue().set(cacheKey, cacheValue,
                        config.getCacheMinutes(), TimeUnit.MINUTES);
            } catch (Exception ignored) {
                // 缓存失败不影响结果
            }
            return ToolResult.success(normalized);
        } catch (Exception e) {
            return ToolResult.fail("WEB_SEARCH_FAILED", "外部搜索失败: " + e.getMessage(), true);
        }
    }
}
