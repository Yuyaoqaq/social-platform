package com.cy.share.ai.agent.workflow;

/**
 * Agent SSE 事件发布器函数式接口，工作流各阶段通过此接口将进度事件推送给 Controller 的 SSE 通道。
 */
@FunctionalInterface
public interface AgentEventPublisher {
    void publish(String event, Object data);
}
