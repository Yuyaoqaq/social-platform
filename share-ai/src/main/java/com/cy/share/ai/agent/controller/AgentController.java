package com.cy.share.ai.agent.controller;

import com.cy.share.ai.agent.auth.AgentRateLimitService;
import com.cy.share.ai.agent.memory.AiConversation;
import com.cy.share.ai.agent.memory.AiMessage;
import com.cy.share.ai.agent.memory.ConversationMemoryService;
import com.cy.share.ai.agent.memory.MemoryPage;
import com.cy.share.ai.agent.model.AgentChatRequest;
import com.cy.share.ai.agent.service.OpsAgentService;
import com.cy.share.ai.agent.config.AgentProperties;
import com.cy.share.ai.agent.model.AgentAnswer;
import com.cy.share.ai.agent.model.AgentTokenUsage;
import com.cy.share.ai.agent.trace.AgentTraceService;
import com.cy.share.ai.agent.trace.AiTrace;
import com.cy.share.ai.agent.trace.TraceDetail;
import com.cy.share.common.constant.ResultCode;
import com.cy.share.common.utils.Result;
import com.cy.share.common.utils.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Agent REST API 控制器，暴露对话流式 SSE 接口、链路追踪查询和会话记忆分页查询。
 * 是 Agent 功能的外部 HTTP 入口，负责参数校验、限流、SSE 推送和追踪记录。
 */
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentController {

    private final OpsAgentService opsAgentService;
    private final ConversationMemoryService memoryService;
    private final AgentRateLimitService rateLimitService;
    private final AgentTraceService traceService;
    private final AgentProperties agentProperties;
    @Qualifier("agentExecutor")
    private final Executor agentExecutor;

    //属性2 ： 告诉浏览器这是一个流，要边接收边显示
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody AgentChatRequest request) {
        Integer userId = Integer.valueOf(UserContext.get());
        String traceId = traceService.start(userId, agentProperties.getModel());
        //如果超过这个时间连接还没关闭，Spring 会自动超时断开。底层是http
        SseEmitter emitter = new SseEmitter(180_000L);

        //限流
        if (!rateLimitService.allowChat(String.valueOf(userId))) {
            Map<String, Object> data = Map.of("message", "请求太频繁，请稍后再试", "traceId", traceId);
            send(emitter, "workflow_failed", data);
            traceService.complete(traceId, "REJECTED", AgentTokenUsage.empty(), "请求太频繁");
            //正常结束，关闭连接
            emitter.complete();
            return emitter;
        }

        //异步执行chat()
        agentExecutor.execute(() -> {
            try {
                send(emitter, "trace_started", Map.of("traceId", traceId));
                AgentAnswer answer = opsAgentService.chat(request, userId, (event, data) -> {
                    send(emitter, event, data);
                    recordEvent(traceId, event, data);
                });
                //绑定会话ID到追踪记录---TODO 目前没用
                traceService.bindConversation(traceId, answer.getConversationId());
                //记录一次完整会话的追踪记录（TODO 状态 ？、令牌使用、异常信息）
                traceService.complete(traceId, answer.getStatus(), answer.getTokenUsage(), null);
                send(emitter, "trace_completed", Map.of("traceId", traceId));
                //正常结束，关闭连接
                emitter.complete();
            } catch (Exception e) {
                send(emitter, "workflow_failed", Map.of("message", safeMessage(e)));
                traceService.complete(traceId, "FAILED", AgentTokenUsage.empty(), safeMessage(e));
                // 异常结束，关闭连接
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    //链路追踪列表查询
    @GetMapping("/traces")
    public Result<List<AiTrace>> traces(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "20") Integer size) {
        return Result.success(traceService.list(Integer.valueOf(UserContext.get()), page, size));
    }

    //某次trace详情查询
    @GetMapping("/traces/{traceId}")
    public Result<TraceDetail> trace(@PathVariable("traceId") String traceId) {
        try {
            return Result.success(traceService.detail(traceId, Integer.valueOf(UserContext.get())));
        } catch (IllegalArgumentException e) {
            return Result.fail(ResultCode.NOT_FOUND, e.getMessage());
        }
    }

    //会话列表查询
    @GetMapping("/conversations")
    public Result<List<AiConversation>> conversations(
            @RequestParam(value = "page", defaultValue = "1") Integer page) {
        MemoryPage<AiConversation> result = memoryService.list(
                Integer.valueOf(UserContext.get()), page);
        return Result.success(result.records(), result.total());
    }

    //某次会话消息列表查询
    @GetMapping("/conversations/{conversationId}/messages")
    public Result<List<AiMessage>> messages(
            @PathVariable("conversationId") Long conversationId,
            @RequestParam(value = "page", defaultValue = "1") Integer page) {
        try {
            MemoryPage<AiMessage> result = memoryService.messages(
                    conversationId, Integer.valueOf(UserContext.get()), page);
            return Result.success(result.records(), result.total());
        } catch (IllegalArgumentException e) {
            return Result.fail(ResultCode.NOT_FOUND, e.getMessage());
        }
    }

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event) // 事件名称
                    .data(data)); // 数据
        } catch (Exception ignored) {
            // 客户端断开后无需继续写入
        }
    }

    private String safeMessage(Exception e) {
        return e.getMessage() == null ? "Agent 执行失败" : e.getMessage();
    }

    //记录链路追踪流水
    private void recordEvent(String traceId, String event, Object data) {
        if ("workflow_completed".equals(event)) return;
        //本阶段类型
        String type = event.startsWith("tool_") ? "TOOL"
                : event.startsWith("model_") ? "LLM"
                : event.startsWith("rag_") ? "RAG"
                : event.startsWith("memory_") ? "MEMORY" : "WORKFLOW";
        //本阶段耗时
        long duration = longValue(data, "durationMs");
        //本阶段令牌使用
        AgentTokenUsage usage = data instanceof Map<?, ?> map
                ? new AgentTokenUsage(longValue(map, "promptTokens"),
                longValue(map, "completionTokens"), longValue(map, "totalTokens"))
                : AgentTokenUsage.empty();
        //本阶段状态
        String status = event.endsWith("failed") ? "FAILED" : "SUCCESS";
        if (data instanceof Map<?, ?> map && map.get("status") != null) {
            status = String.valueOf(map.get("status"));
        }
        traceService.span(traceId, type, event, status, duration, usage, data);
    }

    private long longValue(Object data, String key) {
        return data instanceof Map<?, ?> map ? longValue(map, key) : 0L;
    }

    //返回Map中key对应的long值，如果不存在则返回0L
    private long longValue(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number number) return number.longValue();
        try { return value == null ? 0L : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException e) { return 0L; }
    }
}
