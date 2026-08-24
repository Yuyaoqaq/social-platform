package com.cy.share.ai.agent.tool;

import com.cy.share.ai.agent.config.AgentProperties;
import com.cy.share.ai.agent.mcp.McpStdioClient;
import com.cy.share.ai.agent.model.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 通过本地 MCP Server 搜索小红书高赞图文笔记。 */
@Component
@RequiredArgsConstructor
public class XhsHotNoteMcpTool implements AgentTool {

    private final AgentProperties properties;
    private final McpStdioClient mcpClient;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "search_xhs_hot_note";
    }

    @Override
    public String description() {
        return "搜索小红书公开内容，筛选图文并按最多点赞排序，返回第一篇普通笔记的标题、正文、作者、链接和图片。\n"
                + "什么时候用：用户明确要求查找小红书、高赞笔记、热门图文或参考爆款内容时。\n"
                + "什么时候不用：普通站内内容搜索或不需要小红书素材时。";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "keyword", Map.of(
                                "type", "string",
                                "description", "小红书搜索关键词")),
                "required", List.of("keyword"));
    }

    @Override
    public ToolResult<?> execute(JsonNode arguments, ToolExecutionContext context) {
        if (!properties.getMcp().isEnabled()) {
            return ToolResult.fail("MCP_DISABLED", "小红书 MCP 未启用", false);
        }
        String keyword = arguments.path("keyword").asText("").trim();
        if (keyword.isEmpty()) {
            return ToolResult.fail("INVALID_ARGUMENT", "keyword 不能为空", false);
        }

        AgentProperties.Mcp config = properties.getMcp();
        Map<String, Object> mcpArguments = new LinkedHashMap<>();
        mcpArguments.put("keyword", keyword);
        mcpArguments.put("downloadImages", config.isDownloadImages());
        mcpArguments.put("headless", config.isHeadless());
        if (config.getOutputDir() != null && !config.getOutputDir().isBlank()) {
            mcpArguments.put("outputDir", config.getOutputDir());
        }

        try {
            JsonNode result = mcpClient.callTool(name(), mcpArguments);
            JsonNode content = result.path("content");
            String text = content.isArray() && !content.isEmpty()
                    ? content.get(0).path("text").asText("")
                    : "";
            JsonNode payload = text.isBlank() ? result : objectMapper.readTree(text);
            if (result.path("isError").asBoolean(false) || !payload.path("success").asBoolean(false)) {
                JsonNode error = payload.path("error");
                return ToolResult.fail(
                        error.path("code").asText("MCP_CALL_FAILED"),
                        error.path("message").asText("小红书 MCP 调用失败"),
                        false);
            }
            return ToolResult.success(payload);
        } catch (Exception e) {
            return ToolResult.fail("MCP_CALL_FAILED", "小红书 MCP 调用失败: " + e.getMessage(), true);
        }
    }
}
