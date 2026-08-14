package com.cy.share.ai.agent.trace;

import java.util.List;

/**
 * 追踪详情，将一次请求的追踪主记录和按序排列的 Span 列表组装在一起，供前端查询展示。
 */
public record TraceDetail(AiTrace trace, List<AiTraceSpan> spans) {
}
