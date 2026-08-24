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
        return "查询当前登录用户最近发布的图文（每篇含标题 title、正文 info、标签 tags、发布时间 createTime），"
                + "用于提炼该用户的「创作画像」，作为个性化创作与风格模仿的依据。\n"
                + "参数：size 控制返回篇数（默认 10，最大 20）。\n"
                + "何时调用：当用户要求“按我的风格/口吻写”“模仿我”“照着我以前的写法”“像我的文笔”等个性化创作，或需要参考其历史作品时调用。\n"
                + "调用后，你需要基于返回内容提炼一份结构化画像，至少包含以下维度：\n"
                + "① 语言风格：用词偏好（口语/书面/网络语）、口头禅与高频表达、标点与 emoji 习惯、句子长短节奏；\n"
                + "② 结构偏好：开头与结尾方式、段落组织、标题命名习惯；\n"
                + "③ 内容主题：常写话题与高频标签；\n"
                + "④ 语气与视角：人称、情感基调、正式程度。\n"
                + "何时不调用：用户请求与“个人风格/历史作品”无关的通用创作（如“帮我写一首诗”“写一段文案”）时，直接按常规处理，不要调用本工具。\n";
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
