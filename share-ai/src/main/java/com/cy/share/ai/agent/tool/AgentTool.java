package com.cy.share.ai.agent.tool;

import com.cy.share.ai.agent.model.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Agent 工具 SPI 接口，定义工具的名称、描述、JSON Schema 输入规范和统一执行方法。
 * 所有可被 LLM 调用的工具（搜索、查详情、生图等）均需实现此接口。
 */
public interface AgentTool {

    String name();

    String description();

    Map<String, Object> inputSchema();

    ToolResult<?> execute(JsonNode arguments, ToolExecutionContext context);
}
