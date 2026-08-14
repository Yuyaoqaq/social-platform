package com.cy.share.ai.agent.trace;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * AI 请求追踪实体，映射 ai_trace 表，记录每次 Agent 对话请求的完整链路信息（状态、模型、Token 消耗、耗时）。
 */
@Data
@TableName("ai_trace")
public class AiTrace {
    /** 追踪主键，每次 Agent 请求生成的 UUID，作为整条链路的唯一标识 */
    @TableId
    private String traceId;
    /** 发起请求的用户 ID */
    private Integer userId;
    /** 关联的会话 ID（memory 中的对话） */
    private Long conversationId;
    /** 请求状态：SUCCESS（成功）/ FAILED（失败） */
    private String status;
    private String model;
    private Long promptTokens;
    private Long completionTokens;
    private Long totalTokens;
    private Long durationMs;
    private String errorMessage;
    private Date startTime;
    private Date endTime;
}
