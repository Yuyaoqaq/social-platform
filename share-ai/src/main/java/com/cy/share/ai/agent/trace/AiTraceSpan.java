package com.cy.share.ai.agent.trace;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 追踪 Span 实体，映射 ai_trace_span 表，记录一次 Traces 内某个阶段（LLM 调用/工具执行/RAG 检索等）的详细信息。
 */
@Data
@TableName("ai_trace_span")
public class AiTraceSpan {
    /** 自增主键，仅用于数据库内部唯一标识 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属追踪 ID，关联 ai_trace.trace_id，标识该 Span 属于哪次请求 */
    private String traceId;

    /** 阶段序号，同一 traceId 内按执行顺序递增，用于按顺序还原执行过程 */
    private Integer sequenceNo;

    /** 阶段类型：TOOL（工具执行）/ LLM（模型调用）/ RAG（检索）/ MEMORY（记忆）/ WORKFLOW（工作流） */
    private String spanType;

    /** 阶段名称，即触发时的事件名（如 tool_search_content、model_completed 等） */
    private String spanName;

    /** 阶段状态：SUCCESS（成功）/ FAILED（失败） */
    private String status;
    /** 该阶段耗时（毫秒） */
    private Long durationMs;
    private Long promptTokens;
    private Long completionTokens;
    private Long totalTokens;

    /** 阶段详情 JSON，保存该阶段产生的结构化数据（工具参数/结果、RAG 检索片段等），可为空 */
    private String detailJson;
    private Date createTime;
}
