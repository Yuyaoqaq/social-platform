package com.cy.share.ai.agent.trace;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cy.share.ai.agent.model.AgentTokenUsage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent 请求追踪服务，负责创建和完成追踪记录、按阶段写入 Span（LLM/Tool/RAG/Memory/Workflow），
 * 并提供按用户分页查询和明细组装，用于前端展示每次对话的执行过程和 Token 消耗。
 */
@Service
@RequiredArgsConstructor
public class AgentTraceService {
    private final AiTraceMapper traceMapper;
    private final AiTraceSpanMapper spanMapper;
    private final ObjectMapper objectMapper;
    //traceId ---> sequenceNo 映射//保存当前对话的traceid&序列号
    private final Map<String, AtomicInteger> sequences = new ConcurrentHashMap<>();

    // 一次对话开始，一个traceId，创建追踪记录
    public String start(Integer userId, String model) {
        String traceId = UUID.randomUUID().toString();
        AiTrace trace = new AiTrace();
        trace.setTraceId(traceId);
        trace.setUserId(userId);
        trace.setStatus("RUNNING");
        trace.setModel(model);
        trace.setPromptTokens(0L);
        trace.setCompletionTokens(0L);
        trace.setTotalTokens(0L);
        trace.setDurationMs(0L);
        trace.setStartTime(new Date());
        traceMapper.insert(trace);
        sequences.put(traceId, new AtomicInteger());
        return traceId;
    }
    //完成一次对话，更新追踪记录
    public void complete(String traceId, String status, AgentTokenUsage usage, String error) {
        AiTrace trace = traceMapper.selectById(traceId);
        if (trace == null) return;
        Date end = new Date();
        trace.setStatus(status);
        trace.setEndTime(end);
        trace.setDurationMs(Math.max(0, end.getTime() - trace.getStartTime().getTime()));
        AgentTokenUsage safeUsage = usage == null ? AgentTokenUsage.empty() : usage;
        trace.setPromptTokens(safeUsage.promptTokens());
        trace.setCompletionTokens(safeUsage.completionTokens());
        trace.setTotalTokens(safeUsage.totalTokens());
        trace.setErrorMessage(abbreviate(error, 500));
        traceMapper.updateById(trace);
        sequences.remove(traceId);
    }
    //持久化某次跟踪中的一次流水
    public void span(String traceId, String type, String name, String status,
                     long durationMs, AgentTokenUsage usage, Object detail) {
        AiTraceSpan span = new AiTraceSpan();
        span.setTraceId(traceId);
        //如果指定的 Key（traceId）在 Map 里不存在，我就执行后面的 Lambda 表达式算出一个新 Value 塞进去，然后返回这个新 Value；
        //如果 Key 已经存在，我什么都不做，直接把现有的 Value 返回给你。
        span.setSequenceNo(sequences.computeIfAbsent(traceId, key -> new AtomicInteger()).incrementAndGet());
        span.setSpanType(type);
        span.setSpanName(name);
        span.setStatus(status);
        span.setDurationMs(Math.max(durationMs, 0));
        AgentTokenUsage safeUsage = usage == null ? AgentTokenUsage.empty() : usage;
        span.setPromptTokens(safeUsage.promptTokens());
        span.setCompletionTokens(safeUsage.completionTokens());
        span.setTotalTokens(safeUsage.totalTokens());
        span.setDetailJson(write(detail));
        span.setCreateTime(new Date());
        spanMapper.insert(span);
    }
    //绑定会话ID到追踪记录
    public void bindConversation(String traceId, Long conversationId) {
        AiTrace trace = traceMapper.selectById(traceId);
        if (trace == null) return;
        trace.setConversationId(conversationId);
        traceMapper.updateById(trace);
    }

    //按用户分页查询追踪记录，按开始时间倒序排列
    public List<AiTrace> list(Integer userId, int page, int size) {
        return traceMapper.selectPage(Page.of(Math.max(page, 1), Math.min(Math.max(size, 1), 50)),
                new LambdaQueryWrapper<AiTrace>()
                        .eq(AiTrace::getUserId, userId)
                        .orderByDesc(AiTrace::getStartTime)).getRecords();
    }

    //查询某次追踪的明细
    public TraceDetail detail(String traceId, Integer userId) {
        AiTrace trace = traceMapper.selectOne(new LambdaQueryWrapper<AiTrace>()
                .eq(AiTrace::getTraceId, traceId).eq(AiTrace::getUserId, userId));
        if (trace == null) throw new IllegalArgumentException("链路不存在");
        List<AiTraceSpan> spans = spanMapper.selectList(new LambdaQueryWrapper<AiTraceSpan>()
                .eq(AiTraceSpan::getTraceId, traceId).orderByAsc(AiTraceSpan::getSequenceNo));
        return new TraceDetail(trace, spans);
    }

    //转为 JSON 字符串，如果失败则截取前2000个字符
    private String write(Object value) {
        if (value == null) return null;
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception e) { return abbreviate(String.valueOf(value), 2000); }
    }

    private String abbreviate(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max) + "...";
    }
}
