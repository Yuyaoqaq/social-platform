package com.cy.share.ai.agent.tool;

import com.cy.share.ai.agent.model.ToolResult;
import com.cy.share.api.OpsContentService;
import com.cy.share.vo.MyContentVo;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 查询当前登录用户发布过的图文（含正文、标题、标签），供模型分析作者创作风格。
 */
@Component
public class ContentListTool implements AgentTool {

    @DubboReference(check = false, timeout = 5000)
    private OpsContentService opsContentService;

    @Override
    public String name() {
        return "my_content";
    }

    @Override
    public String description() {
        return "查询当前登录用户发布过的图文正文、标题和标签，用于分析其创作风格或引用其历史作品。\n"
                + "什么时候用：当用户的请求中包含“模仿我的风格”、“照着我这个写”、“跟我以前一样”、“像我的文笔”等明确要求个人化创作的意图时，你需要调用此工具，通过分析历史内容，提取用户的用语习惯、结构偏好和逻辑模式，作为后续生成个性化回复的依据。\n"
                + "什么时候不用：如果用户只是说“帮我写一首诗”而没有提到“模仿我”，则不要调用本工具，直接按常规创作处理。\n";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "size", Map.of("type", "integer", "minimum", 1, "maximum", 20)));
    }

    @Override
    public ToolResult<List<MyContentVo>> execute(JsonNode arguments, ToolExecutionContext context) {
        Integer userId = parseUserId(context.userId());
        if (userId == null) {
            return ToolResult.fail("UNAUTHENTICATED", "无法获取当前登录用户", false);
        }
        int size = Math.max(1, Math.min(arguments.path("size").asInt(10), 20));
        return ToolResult.success(opsContentService.listMyContent(userId, size));
    }

    private Integer parseUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        return Integer.valueOf(userId.trim());
    }
}
