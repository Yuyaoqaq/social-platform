package com.cy.share.ai.agent.tool;

import com.cy.share.ai.agent.model.ToolResult;
import com.cy.share.api.OpsContentService;
import com.cy.share.vo.SearchFeedVo;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 站内图文搜索工具，通过 Dubbo 调用运营内容服务按关键词/标签检索帖子。
 */
@Component
public class ContentSearchTool implements AgentTool {

    @DubboReference(check = false, timeout = 5000)
    private OpsContentService opsContentService;

    @Override
    public String name() {
        return "search_content";
    }

    @Override
    public String description() {
        return "站内图文搜索：按关键词/标签检索平台已有帖子，用于查找可参考的内容素材、对标同类内容。\n"
                + "什么时候用：用户的提问可以轻松抽取出明确主题、关键词或标签时。\n"
                + "什么时候不用：用户明确要求实时性内容，当前最火爆内容时，请使用 search_external_topics 工具调用站外搜索引擎。";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "keyword", Map.of("type", "string", "description", "搜索关键词"),
                        "tags", Map.of("type", "array", "items", Map.of("type", "string")),
                        "size", Map.of("type", "integer", "minimum", 1, "maximum", 20)),
                "required", List.of("keyword"));
    }

    @Override
    public ToolResult<SearchFeedVo> execute(JsonNode arguments, ToolExecutionContext context) {
        String keyword = arguments.path("keyword").asText("").trim();
        if (keyword.isEmpty()) {
            return ToolResult.fail("INVALID_ARGUMENT", "keyword 不能为空", false);
        }
        int size = Math.max(1, Math.min(arguments.path("size").asInt(10), 20));
        List<String> tags = new ArrayList<>();
        arguments.path("tags").forEach(node -> tags.add(node.asText()));
        return ToolResult.success(opsContentService.searchContent(keyword, null, size, tags));
    }
}
