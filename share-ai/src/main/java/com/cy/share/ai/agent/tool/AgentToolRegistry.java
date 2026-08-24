package com.cy.share.ai.agent.tool;

import com.cy.share.ai.agent.config.AgentProperties;
import com.cy.share.ai.agent.model.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Agent 工具注册与调度中心，收集所有 AgentTool 实现，按上下文过滤可用工具，
 * 为 LLM 生成 Function Calling 的工具列表，并负责实际的工具分发执行与参数解析。
 */
@Component
public class AgentToolRegistry {

    private final Map<String, AgentTool> tools;
    private final ObjectMapper objectMapper;
    private final AgentProperties properties;
    //name ---> AgentTool实体映射
    //Spring 有个特殊机制：当构造器参数是 List<X>（或 Map<String, X>）时，它会自动收集容器里所有类型为 X 的 Bean，打包成一个集合注入进来。
    public AgentToolRegistry(List<AgentTool> tools,
                             ObjectMapper objectMapper,
                             AgentProperties properties) {
        this.tools = tools.stream().collect(Collectors.toMap(
                AgentTool::name,
                Function.identity(),
                (left, right) -> left,
                LinkedHashMap::new));
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    //把自家工具（AgentTool）翻译成 OpenAI 能听懂的外语（API 格式）
    public List<OpenAiApi.FunctionTool> functionTools(ToolExecutionContext context) {
        return tools.values().stream()
                .filter(tool -> isEnabled(tool.name(), context))
                .map(tool -> new OpenAiApi.FunctionTool(
                        new OpenAiApi.FunctionTool.Function(
                                tool.description(), tool.name(), tool.inputSchema(), false)))
                .toList();
    }
    //返回工具执行结果ToolResult
    public String execute(String toolName, String arguments, ToolExecutionContext context) {
        AgentTool tool = tools.get(toolName);
        if (tool == null || !isEnabled(toolName, context)) {
            return write(ToolResult.fail("TOOL_NOT_ALLOWED", "工具不可用: " + toolName, false));
        }
        try {
            JsonNode json = arguments == null || arguments.isBlank()
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(arguments);
            return write(tool.execute(json, context));
        } catch (Exception e) {
            return write(ToolResult.fail("TOOL_ARGUMENT_ERROR", e.getMessage(), false));
        }
    }

    //从上下文中判断工具是否可用
    private boolean isEnabled(String toolName, ToolExecutionContext context) {
        if ("search_external_topics".equals(toolName)) {
            return context.webSearchEnabled();
        }
        if ("generate_image".equals(toolName)) {
            return context.imageGenerationEnabled();
        }
        if ("search_xhs_hot_note".equals(toolName)) {
            return properties.getMcp().isEnabled() && context.webSearchEnabled();
        }
        return true;
    }

    //转换为 JSON 字符串
    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{\"success\":false,\"code\":\"TOOL_SERIALIZE_ERROR\"}";
        }
    }
}
