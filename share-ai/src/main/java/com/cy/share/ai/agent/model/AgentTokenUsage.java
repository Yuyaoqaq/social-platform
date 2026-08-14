package com.cy.share.ai.agent.model;

/**
 * Token 用量记录，汇总一次对话中 prompt/completion/total 三类 token 消耗。
 */
public record AgentTokenUsage(long promptTokens,
                              long completionTokens,
                              long totalTokens) {

    public static AgentTokenUsage empty() {
        return new AgentTokenUsage(0, 0, 0);
    }
}
