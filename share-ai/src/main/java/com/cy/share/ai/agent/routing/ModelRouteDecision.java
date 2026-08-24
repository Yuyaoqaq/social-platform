package com.cy.share.ai.agent.routing;

/** Python 路由器返回的本轮模型选择。 */
public record ModelRouteDecision(String intent,
                                 boolean simple,
                                 int complexityScore,
                                 String model,
                                 int maxTokens,
                                 boolean requiresTools,
                                 boolean fallback) {
}
