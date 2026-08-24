package com.cy.share.ai.agent.tool;

import com.cy.share.ai.agent.auth.AgentRateLimitService;
import com.cy.share.ai.agent.config.AgentProperties;
import com.cy.share.ai.agent.model.ImageGenerationResult;
import com.cy.share.ai.agent.model.ToolResult;
import com.cy.share.api.GeneratedImageStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * AI 图片生成工具，调用阿里百炼 DashScope 多模态生图 API 生成配图，
 * 结果通过 Dubbo 存储服务持久化，受每日限额控制。
 */
@Component
@RequiredArgsConstructor
public class ImageGenerationTool implements AgentTool {

    private final AgentProperties properties;
    private final AgentRateLimitService rateLimitService;
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    @DubboReference(check = false, timeout = 60000)
    private GeneratedImageStorageService generatedImageStorageService;

    @Override
    public String name() {
        return "generate_image";
    }

    @Override
    public String description() {
        return "调用生图模型生成内容配图。\n"
                + "什么时候用：仅当用户明确要求生成图片或配图时。\n"
                + "什么时候不用：用户没有明确要求时不要主动生成；受每日次数限制，不要为纯文本回答擅自配图。";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "prompt", Map.of("type", "string", "description", "图片内容描述"),
                        "style", Map.of("type", "string", "description", "图片风格，可不填"),
                        "aspectRatio", Map.of("type", "string", "enum", List.of("1:1", "3:4", "4:3"))),
                "required", List.of("prompt"));
    }

    @Override
    public ToolResult<ImageGenerationResult> execute(JsonNode arguments, ToolExecutionContext context) {
        AgentProperties.Image config = properties.getImage();
        if (!config.isEnabled() || !StringUtils.hasText(config.getApiKey())) {
            return ToolResult.fail("IMAGE_NOT_CONFIGURED", "百炼生图 API Key 未配置", false);
        }
        String submitUrl = resolveSubmitUrl(config);
        if (!StringUtils.hasText(submitUrl)) {
            return ToolResult.fail("IMAGE_WORKSPACE_NOT_CONFIGURED",
                    "百炼 WorkspaceId 未配置，请设置 DASHSCOPE_WORKSPACE_ID", false);
        }
        if (!rateLimitService.allowImage(context.userId())) {
            return ToolResult.fail("IMAGE_RATE_LIMIT", "今日生图次数已用完", false);
        }

        String prompt = arguments.path("prompt").asText("").trim();
        if (prompt.isEmpty()) {
            return ToolResult.fail("INVALID_ARGUMENT", "prompt 不能为空", false);
        }
        String style = arguments.path("style").asText("").trim();
        if (!style.isEmpty()) {
            prompt = prompt + "，风格：" + style;
        }
        //转换格式
        String size = switch (arguments.path("aspectRatio").asText("1:1")) {
            case "3:4" -> "768*1024";
            case "4:3" -> "1024*768";
            default -> "1024*1024";
        };

        try {
            RestClient client = restClientBuilder.build();
            Map<String, Object> request = Map.of(
                    "model", config.getModel(),
                    "input", Map.of("messages", List.of(Map.of(
                            "role", "user",
                            "content", List.of(Map.of("text", prompt))))),
                    "parameters", Map.of(
                            "negative_prompt", "低分辨率，低画质，肢体畸形，手指畸形，乱码文字，品牌商标",
                            "prompt_extend", true,
                            "watermark", false,
                            "size", size));

            String submitBody = client.post()
                    .uri(submitUrl)
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);
            JsonNode submit = objectMapper.readTree(submitBody);
            String imageUrl = submit.path("output")
                    .path("choices").path(0)
                    .path("message").path("content").path(0)
                    .path("image").asText("");
            if (!StringUtils.hasText(imageUrl)) {
                String code = submit.path("code").asText("IMAGE_GENERATION_FAILED");
                String message = submit.path("message").asText("生图接口未返回图片 URL");
                return ToolResult.fail(code, message, true);
            }
            String requestId = submit.path("request_id").asText("");
            String permanentUrl = generatedImageStorageService.storeFromUrl(
                    imageUrl, context.userId());
            return ToolResult.success(new ImageGenerationResult(
                    requestId, permanentUrl, config.getModel(), prompt));
        } catch (Exception e) {
            return ToolResult.fail("IMAGE_GENERATION_FAILED", "生图调用失败: " + e.getMessage(), true);
        }
    }

    private String resolveSubmitUrl(AgentProperties.Image config) {
        if (StringUtils.hasText(config.getSubmitUrl())) {
            return config.getSubmitUrl();
        }
        if (!StringUtils.hasText(config.getWorkspaceId())) {
            return null;
        }
        return "https://" + config.getWorkspaceId()
                + ".cn-beijing.maas.aliyuncs.com/api/v1/services/aigc/"
                + "multimodal-generation/generation";
    }
}
