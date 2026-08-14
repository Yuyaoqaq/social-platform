package com.cy.share.ai.agent.tool;

import com.cy.share.ai.agent.model.ToolResult;
import com.cy.share.api.OpsContentService;
import com.cy.share.vo.LogDetailVo;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 图文详情查询工具，根据图文 ID 获取完整标题、正文、作者、图片和点赞信息。
 */
@Component
public class ContentDetailTool implements AgentTool {

    @DubboReference(check = false, timeout = 5000)
    private OpsContentService opsContentService;

    @Override
    public String name() {
        return "get_content_detail";
    }

    @Override
    public String description() {
        return "根据图文 ID 查询完整标题、正文、作者、图片和点赞信息";
    }

    // JSON Schema 官方定义
    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of("logId", Map.of("type", "integer", "description", "图文 ID")),
                "required", List.of("logId"));
    }

    @Override
    public ToolResult<LogDetailVo> execute(JsonNode arguments, ToolExecutionContext context) {
        int logId = arguments.path("logId").asInt(0);
        LogDetailVo detail = opsContentService.getContentDetail(logId);
        if (detail == null) {
            return ToolResult.fail("CONTENT_NOT_FOUND", "没有找到对应图文", false);
        }
        return ToolResult.success(detail);
    }
}
