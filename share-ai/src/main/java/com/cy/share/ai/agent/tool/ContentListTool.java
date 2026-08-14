package com.cy.share.ai.agent.tool;

import com.cy.share.ai.agent.model.ToolResult;
import com.cy.share.api.OpsContentService;
import com.cy.share.dto.QueryDto;
import com.cy.share.vo.FeedVo;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 图文列表查询工具，获取最新图文或指定作者发布的图文列表。
 */
@Component
public class ContentListTool implements AgentTool {

    @DubboReference(check = false, timeout = 5000)
    private OpsContentService opsContentService;

    @Override
    public String name() {
        return "list_content";
    }

    @Override
    public String description() {
        return "查询最新图文，或者查询指定作者发布的图文";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "author", Map.of("type", "string", "description", "作者名，可不填"),
                        "size", Map.of("type", "integer", "minimum", 1, "maximum", 20)));
    }

    @Override
    public ToolResult<FeedVo> execute(JsonNode arguments, ToolExecutionContext context) {
        QueryDto query = new QueryDto();
        query.setAuthor(arguments.path("author").asText(null));
        query.setSize(Math.max(1, Math.min(arguments.path("size").asInt(10), 20)));
        return ToolResult.success(opsContentService.listContent(query));
    }
}
