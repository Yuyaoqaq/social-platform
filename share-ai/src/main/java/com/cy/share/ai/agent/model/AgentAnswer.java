package com.cy.share.ai.agent.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 对话响应 DTO，承载模型返回的答案、推荐、草稿、引用来源、工具调用记录和 Token 用量。
 */
@Data
public class AgentAnswer {
    private Long conversationId;
    private String status = "COMPLETED";
    private String answer;
    private List<String> recommendations = new ArrayList<>();
    private AgentDraft draft;
    private List<AgentSource> sources = new ArrayList<>();
    private List<AgentToolCallRecord> toolCalls = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private AgentTokenUsage tokenUsage = AgentTokenUsage.empty();
}
