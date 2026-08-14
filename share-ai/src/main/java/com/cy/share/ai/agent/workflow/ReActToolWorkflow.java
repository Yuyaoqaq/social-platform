package com.cy.share.ai.agent.workflow;

import com.cy.share.ai.agent.config.AgentProperties;
import com.cy.share.ai.agent.memory.AiMessage;
import com.cy.share.ai.agent.memory.MemoryContext;
import com.cy.share.ai.agent.model.AgentToolCallRecord;
import com.cy.share.ai.agent.model.AgentTokenUsage;
import com.cy.share.ai.agent.rag.RagContext;
import com.cy.share.ai.agent.tool.AgentToolRegistry;
import com.cy.share.ai.agent.tool.ToolExecutionContext;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * ReAct（Reason + Act）工具调用循环引擎，将系统提示词、历史记忆、RAG 上下文和用户消息组装为 Prompt，
 * 在最大步数内反复调用 LLM 执行工具，直至模型输出最终答案或达到步数上限。
 */
@Service
@RequiredArgsConstructor
public class ReActToolWorkflow {

    private static final String SYSTEM_PROMPT = """
            你是短图文平台的内容运营助手。
            你可以搜索站内图文、读取详情、搜索外部公开主题，并在用户明确要求时生成图片。
            外部搜索排名不等于平台真实热度，禁止伪造点赞、收藏、评论和热度数据。
            工具结果失败时可以使用已有信息继续回答，但必须说明缺失。
            不允许发布、修改、点赞或操作业务数据。
            最终只输出合法 JSON，字段为：
            answer(string), recommendations(string数组), draft({title,content,tags,images}),
            sources([{type,title,url,source}]), warnings(string数组)。
            draft.images 只能包含可直接访问的 http 或 https 图片 URL。
            图片 URL 可以来自站内图文的 picurls、知识库中已确认可使用的图片地址，或者生图工具返回结果。
            禁止在 draft.images 中填写“建议添加图片”、图片描述、生图提示词或其他非 URL 内容。
            没有可用图片 URL 时，draft.images 必须返回空数组。
            不要输出 Markdown 代码块，不要暴露内部思考过程。
            """;

    private final ChatModel chatModel;
    private final AgentToolRegistry toolRegistry;
    private final AgentProperties properties;
    @Qualifier("agentExecutor")
    private final Executor agentExecutor;

    public ReActResult execute(String userMessage,
                               MemoryContext memory,
                               RagContext rag,
                               ToolExecutionContext toolContext,
                               AgentEventPublisher events) {
        //消息 --包含提示词、历史记忆、RAG结果、用户消息
        List<Message> messages = buildMessages(userMessage, memory, rag);
        //工具调用记录
        List<AgentToolCallRecord> records = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        //
        UsageAccumulator usageAccumulator = new UsageAccumulator();
        String previousSignature = null;

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(properties.getModel())
                .temperature(0.2)
                .tools(toolRegistry.functionTools(toolContext))
                .parallelToolCalls(false)
                .proxyToolCalls(true)
                .build();
        //开启轮转。工具调用循环。一轮支持多个工具
        for (int step = 1; step <= properties.getMaxToolSteps(); step++) {
            events.publish("think_started", java.util.Map.of("step", step));
            long modelStarted = System.nanoTime();
            // 1.think
            ChatResponse response = chatModel.call(new Prompt(messages, options));
            usageAccumulator.add(response);
            events.publish("think_completed", usageEvent(response, step, elapsedMs(modelStarted)));
            AssistantMessage assistant = response.getResult().getOutput();
            messages.add(assistant);
            //3.observe//如果不调用工具，直接结束工作流
            if (!assistant.hasToolCalls()) {
                return new ReActResult(assistant.getText(), records, warnings,
                        usageAccumulator.snapshot());
            }
            //2.act
            List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
            for (AssistantMessage.ToolCall call : assistant.getToolCalls()) {
                //检查工具重复调用的标记
                String signature = call.name() + ":" + call.arguments();
                if (signature.equals(previousSignature)) {
                    String result = "{\"success\":false,\"code\":\"TOOL_REPEAT\",\"message\":\"检测到重复工具调用\"}";
                    responses.add(new ToolResponseMessage.ToolResponse(call.id(), call.name(), result));
                    records.add(new AgentToolCallRecord(call.name(), abbreviate(call.arguments(), 500),
                            "REJECTED", "重复调用已终止"));
                    warnings.add("检测到重复工具调用，已提前结束工具循环");
                    messages.add(new ToolResponseMessage(responses));
                    return finishWithoutTools(messages, records, warnings, usageAccumulator, events);
                }
                previousSignature = signature;

                events.publish("tool_started", java.util.Map.of("name", call.name(), "step", step));
                long toolStarted = System.nanoTime();
                String result = executeWithTimeout(call, toolContext);
                //AI框架的Toolresponse集合/一轮工具调用一个
                responses.add(new ToolResponseMessage.ToolResponse(call.id(), call.name(), result));
                String status = result.contains("\"success\":true") ? "SUCCESS" : "FAILED";
                //自定义的工具调用记录集合/一个对话一个
                records.add(new AgentToolCallRecord(call.name(), abbreviate(call.arguments(), 500),
                        status, abbreviate(result, 800)));
                events.publish("tool_completed", java.util.Map.of(
                        "name", call.name(), "status", status, "step", step,
                        "durationMs", elapsedMs(toolStarted)));
            }
            //一个对话一个（提示词/历史记忆/用户消息/工具调用结果）
            messages.add(new ToolResponseMessage(responses));
        }

        warnings.add("工具调用达到最大步数，已使用现有结果生成回答");
        return finishWithoutTools(messages, records, warnings, usageAccumulator, events);
    }

    //工具调用超时或重复调用，直接退出工作流，使用已有信息让ai生成最终回答
    private ReActResult finishWithoutTools(List<Message> messages,
                                           List<AgentToolCallRecord> records,
                                           List<String> warnings,
                                           UsageAccumulator usageAccumulator,
                                           AgentEventPublisher events) {
        messages.add(new UserMessage("停止调用工具，请根据已有信息直接输出最终 JSON。"));
        OpenAiChatOptions finalOptions = OpenAiChatOptions.builder()
                .model(properties.getModel())
                .temperature(0.2)
                .build();
        events.publish("think_started", java.util.Map.of("step", -1));
        long modelStarted = System.nanoTime();
        ChatResponse finalResponse = chatModel.call(new Prompt(messages, finalOptions));
        usageAccumulator.add(finalResponse);
        events.publish("think_completed", usageEvent(finalResponse, -1, elapsedMs(modelStarted)));
        return new ReActResult(finalResponse.getResult().getOutput().getText(), records, warnings,
                usageAccumulator.snapshot());
    }

    //异步工具调用，并设置超时
    private String executeWithTimeout(AssistantMessage.ToolCall call, ToolExecutionContext context) {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(
                () -> toolRegistry.execute(call.name(), call.arguments(), context), agentExecutor);
        int timeout = "generate_image".equals(call.name())
                ? Math.max(properties.getToolTimeoutSeconds(), properties.getImage().getTimeoutSeconds() + 5)
                : properties.getToolTimeoutSeconds();
        try {
            return future.get(timeout, TimeUnit.SECONDS);
        } catch (Exception e) {
            future.cancel(true);
            return "{\"success\":false,\"code\":\"TOOL_TIMEOUT\",\"message\":\"工具调用超时\",\"retryable\":true}";
        }
    }

    //把提示词/历史记忆/rag结果/用户对话/全塞进去
    private List<Message> buildMessages(String userMessage, MemoryContext memory, RagContext rag) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        if (memory.summary() != null && !memory.summary().isBlank()) {
            messages.add(new SystemMessage("历史对话摘要：\n" + memory.summary()));
        }
        for (AiMessage message : memory.recentMessages()) {
            if ("USER".equals(message.getRole())) {
                messages.add(new UserMessage(message.getContent()));
            } else if ("ASSISTANT".equals(message.getRole())) {
                messages.add(new AssistantMessage(message.getContent()));
            }
        }
        messages.add(new SystemMessage("RAG 子流程结果：\n" + rag.augmentedQuery()));
        messages.add(new UserMessage(userMessage));
        return messages;
    }

    private String abbreviate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...";
    }

    //封装工具调用使用情况（步数/耗时/提示词令牌/完成令牌/总令牌）
    private java.util.Map<String, Object> usageEvent(ChatResponse response, int step, long durationMs) {
        Usage usage = response == null || response.getMetadata() == null
                ? null : response.getMetadata().getUsage();
        return java.util.Map.of(
                "step", step,
                "durationMs", durationMs,
                "promptTokens", usage == null ? 0L : usage.getPromptTokens(),
                "completionTokens", usage == null ? 0L : usage.getGenerationTokens(),
                "totalTokens", usage == null ? 0L : usage.getTotalTokens());
    }

    //计算耗时
    private long elapsedMs(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }


    private static final class UsageAccumulator {
        private long promptTokens;
        private long completionTokens;
        private long totalTokens;

        //累计
        private void add(ChatResponse response) {
            if (response == null || response.getMetadata() == null) {
                return;
            }
            Usage usage = response.getMetadata().getUsage();
            if (usage == null) {
                return;
            }
            promptTokens += value(usage.getPromptTokens());
            completionTokens += value(usage.getGenerationTokens());
            totalTokens += value(usage.getTotalTokens());
        }

        //快照
        private AgentTokenUsage snapshot() {
            return new AgentTokenUsage(promptTokens, completionTokens, totalTokens);
        }

        //转换
        private long value(Long tokens) {
            return tokens == null ? 0 : tokens;
        }
    }
}
