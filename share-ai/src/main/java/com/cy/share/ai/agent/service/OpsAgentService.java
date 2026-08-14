package com.cy.share.ai.agent.service;

import com.cy.share.ai.agent.memory.AiConversation;
import com.cy.share.ai.agent.memory.ConversationMemoryService;
import com.cy.share.ai.agent.memory.MemoryContext;
import com.cy.share.ai.agent.model.AgentAnswer;
import com.cy.share.ai.agent.model.AgentChatRequest;
import com.cy.share.ai.agent.model.AgentTokenUsage;
import com.cy.share.ai.agent.rag.RagContext;
import com.cy.share.ai.agent.rag.RagRetrievalService;
import com.cy.share.ai.agent.tool.ToolExecutionContext;
import com.cy.share.ai.agent.workflow.AgentEventPublisher;
import com.cy.share.ai.agent.workflow.ReActResult;
import com.cy.share.ai.agent.workflow.ReActToolWorkflow;
import com.cy.share.ai.agent.workflow.WorkflowState;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 对话编排服务，串接记忆加载、RAG 检索、ReAct 工具循环和结果持久化，
 * 是单次 Agent 对话的完整流程控制器，通过事件发布器向 SSE 通道推送各阶段进度。
 */
@Service
@RequiredArgsConstructor
public class OpsAgentService {

    private final ConversationMemoryService memoryService;
    private final RagRetrievalService ragRetrievalService;
    private final ReActToolWorkflow reActToolWorkflow;
    private final ObjectMapper objectMapper;

    public AgentAnswer chat(AgentChatRequest request, Integer userId, AgentEventPublisher events) {
        //events是函数式接口的实例，lambda表达式是方法体，publish是方法名。
        //此处等价于send(emitter,"workflow_started",WorkflowState.RECEIVED);
        events.publish("workflow_started", WorkflowState.RECEIVED);
        events.publish("workflow_state", WorkflowState.MEMORY);
        long memoryStarted = System.nanoTime();
        AiConversation conversation = memoryService.open(userId, request.getConversationId(), request.getMessage());
        // 加载对话记忆
        MemoryContext memory = memoryService.load(conversation.getId(), userId);
        // 保存用户消息
        memoryService.saveMessage(conversation.getId(), userId, "USER", request.getMessage());
        events.publish("memory_completed", java.util.Map.of(
                "durationMs", elapsedMs(memoryStarted),
                "recentMessages", memory.recentMessages().size(),
                "hasSummary", memory.summary() != null && !memory.summary().isBlank()));

        events.publish("workflow_state", WorkflowState.RETRIEVING);
        long ragStarted = System.nanoTime();
        // 开启RAG检索
        RagContext rag = ragRetrievalService.retrieve(request.getMessage());
        events.publish("rag_completed", java.util.Map.of(
                "empty", rag.empty(),
                "sources", rag.sources().size(),
                "durationMs", elapsedMs(ragStarted)));

        events.publish("workflow_state", WorkflowState.TOOL_EXECUTING);
        ToolExecutionContext toolContext = new ToolExecutionContext(
                String.valueOf(userId),
                request.isEnableWebSearch(),
                request.isEnableImageGeneration());
        // 开启ReAct工具循环
        ReActResult reAct = reActToolWorkflow.execute(
                request.getMessage(), memory, rag, toolContext, events);

        events.publish("workflow_state", WorkflowState.FORMATTING);
        long formattingStarted = System.nanoTime();
        AgentAnswer answer = parseAnswer(reAct.content());
        answer.setConversationId(conversation.getId());
        answer.getToolCalls().addAll(reAct.toolCalls());
        answer.getSources().addAll(0, rag.sources());
        answer.getWarnings().addAll(reAct.warnings());
        answer.setTokenUsage(reAct.tokenUsage());
        if (rag.warning() != null) {
            answer.getWarnings().add(rag.warning());
        }
        if (!answer.getWarnings().isEmpty()) {
            answer.setStatus("PARTIAL_SUCCESS");
        }

        events.publish("formatting_completed", java.util.Map.of(
                "durationMs", elapsedMs(formattingStarted)));
        events.publish("workflow_state", WorkflowState.PERSISTING);
        long persistStarted = System.nanoTime();
        memoryService.saveToolCalls(conversation.getId(), userId, reAct.toolCalls());
        memoryService.saveMessage(conversation.getId(), userId, "ASSISTANT", write(answer));
        memoryService.summarizeIfNeeded(conversation.getId(), userId);
        events.publish("persistence_completed", java.util.Map.of("durationMs", elapsedMs(persistStarted)));
        events.publish("workflow_completed", answer);
        return answer;
    }

    //格式清洗、类型转换、降级处理
    private AgentAnswer parseAnswer(String content) {
        try {
            String json = stripCodeFence(content);
            AgentAnswer answer = objectMapper.readValue(json, AgentAnswer.class);
            normalize(answer);
            return answer;
        } catch (Exception e) {
            AgentAnswer fallback = new AgentAnswer();
            fallback.setAnswer(content);
            fallback.getWarnings().add("模型输出不是标准 JSON，已降级为纯文本");
            return fallback;
        }
    }

    //防御性数据清洗/归一化
    private void normalize(AgentAnswer answer) {
        if (answer.getRecommendations() == null) answer.setRecommendations(new ArrayList<>());
        if (answer.getSources() == null) answer.setSources(new ArrayList<>());
        if (answer.getToolCalls() == null) answer.setToolCalls(new ArrayList<>());
        if (answer.getWarnings() == null) answer.setWarnings(new ArrayList<>());
        if (answer.getTokenUsage() == null) answer.setTokenUsage(AgentTokenUsage.empty());
        if (answer.getDraft() != null) {
            if (answer.getDraft().getTags() == null) {
                answer.getDraft().setTags(new ArrayList<>());
            }
            List<String> imageUrls = answer.getDraft().getImages() == null
                    ? List.of()
                    : answer.getDraft().getImages().stream()
                            .filter(this::isHttpUrl)
                            .distinct()
                            .toList();
            answer.getDraft().setImages(new ArrayList<>(imageUrls));
        }
    }

    // 判断是否为HTTP URL
    private boolean isHttpUrl(String value) {
        if (value == null) {
            return false;
        }
        String url = value.trim().toLowerCase();
        return url.startsWith("http://") || url.startsWith("https://");
    }

    // 数据清洗，去除代码围栏
    private String stripCodeFence(String value) {
        if (value == null) return "{}";
        String trimmed = value.trim();
        if (trimmed.startsWith("```")) {
            int firstLine = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLine >= 0 && lastFence > firstLine) {
                return trimmed.substring(firstLine + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    //专门用来计算从某个时刻到当前时刻，一共过去了多少毫秒
    private long elapsedMs(long startedNanos) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }
}
