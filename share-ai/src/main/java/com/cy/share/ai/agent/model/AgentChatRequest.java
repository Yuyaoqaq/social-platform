package com.cy.share.ai.agent.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Agent 对话请求 DTO，包含用户消息、会话 ID 及功能开关（联网搜索、图片生成）。
 */
@Data
public class AgentChatRequest {

    private Long conversationId;

    @NotBlank(message = "消息不能为空")
    @Size(max = 4000, message = "消息不能超过 4000 字")
    private String message;

    private boolean enableWebSearch = true;
    private boolean enableImageGeneration = false;
}
