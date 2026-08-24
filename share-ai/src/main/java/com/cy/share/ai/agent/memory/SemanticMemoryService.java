package com.cy.share.ai.agent.memory;

import com.cy.share.ai.agent.config.AgentProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 MEMORY.md 的语义记忆服务。
 * 每个用户、每个会话使用独立文件，避免不同用户或会话之间串数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticMemoryService {

    private static final String DEFAULT_TEMPLATE = """
            # 语义记忆

            ## 用户偏好
            - 无

            ## 已确认事实
            - 无

            ## 长期约束
            - 无

            ## 关键决定
            - 无
            """;

    private static final String MEMORY_SYSTEM_PROMPT = """
            你负责维护当前用户、当前会话的语义记忆。

            请判断本轮对话中是否出现了以后仍有帮助的重要事实，并合并进已有 MEMORY.md。
            重要事实包括：稳定偏好、明确身份或背景、长期约束、用户确认的关键决定。

            规则：
            1. 只记录用户明确说过，或工具结果已经确认的事实。
            2. 不记录猜测、客套话、一次性要求、临时数据和助手自己的建议。
            3. 不记录密码、API Key、令牌、身份证号、联系方式等敏感信息。
            4. 新事实与旧事实冲突时，以用户最新明确表达为准。
            5. 去重并保持内容简短；没有变化时只输出 NO_CHANGE。
            6. 有变化时只输出合并后的完整 Markdown，不要代码块，不要解释。
            """;

    private final AgentProperties properties;
    private final ChatModel chatModel;
    private final ConcurrentHashMap<Path, Object> fileLocks = new ConcurrentHashMap<>();

    private Path storageRoot;
    private String template;

    @PostConstruct
    public void initialize() {
        AgentProperties.SemanticMemory config = properties.getSemanticMemory();
        storageRoot = Path.of(config.getStoragePath()).toAbsolutePath().normalize();
        template = loadTemplate(config.getTemplatePath());
        try {
            Files.createDirectories(storageRoot);
            log.info("Semantic memory initialized. root={}", storageRoot);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建语义记忆目录: " + storageRoot, e);
        }
    }

    /** 加载当前用户和会话的语义记忆；首次访问时由模板创建。 */
    public String load(Integer userId, Long conversationId) {
        if (!properties.getSemanticMemory().isEnabled()) {
            return "";
        }
        Path file = memoryFile(userId, conversationId);
        synchronized (lockFor(file)) {
            try {
                if (Files.notExists(file)) {
                    writeAtomically(file, template);
                }
                return limit(Files.readString(file, StandardCharsets.UTF_8));
            } catch (IOException e) {
                log.warn("Semantic memory load failed. userId={}, conversationId={}",
                        userId, conversationId, e);
                return "";
            }
        }
    }

    /**
     * 使用当前会话模型判断本轮是否产生重要事实，有变化时覆盖当前会话自己的 MEMORY.md。
     */
    public void remember(Integer userId,
                         Long conversationId,
                         String userMessage,
                         String assistantAnswer,
                         String conversationModel) {
        AgentProperties.SemanticMemory config = properties.getSemanticMemory();
        if (!config.isEnabled()) {
            return;
        }

        Path file = memoryFile(userId, conversationId);
        synchronized (lockFor(file)) {
            String current = load(userId, conversationId);
            String input = """
                    已有 MEMORY.md：
                    %s

                    本轮用户消息：
                    %s

                    本轮助手结果：
                    %s
                    """.formatted(current, limitInput(userMessage), limitInput(assistantAnswer));

            try {
                OpenAiChatOptions options = OpenAiChatOptions.builder()
                        .model(conversationModel == null || conversationModel.isBlank()
                                ? properties.getModel()
                                : conversationModel)
                        .maxTokens(1200)
                        .temperature(0.0)
                        .build();
                ChatResponse response = chatModel.call(new Prompt(List.of(
                        new SystemMessage(MEMORY_SYSTEM_PROMPT),
                        new UserMessage(input)), options));
                String updated = response == null || response.getResult() == null
                        ? null
                        : response.getResult().getOutput().getText();
                updated = cleanModelOutput(updated);
                if (updated == null || updated.equalsIgnoreCase("NO_CHANGE") || updated.equals(current.trim())) {
                    return;
                }
                if (!updated.startsWith("# 语义记忆")) {
                    log.warn("Semantic memory ignored invalid model output. userId={}, conversationId={}",
                            userId, conversationId);
                    return;
                }
                writeAtomically(file, limit(updated));
            } catch (Exception e) {
                log.warn("Semantic memory update failed. userId={}, conversationId={}",
                        userId, conversationId, e);
            }
        }
    }

    private String loadTemplate(String templatePath) {
        List<String> candidates = List.of(
                templatePath == null ? "" : templatePath,
                "MEMORY.md",
                "../MEMORY.md");
        for (String candidate : candidates) {
            if (candidate.isBlank()) {
                continue;
            }
            Path path = Path.of(candidate).toAbsolutePath().normalize();
            try {
                if (Files.exists(path)) {
                    return Files.readString(path, StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                log.warn("Semantic memory template load failed. path={}", path, e);
            }
        }
        log.warn("MEMORY.md template not found, using built-in template");
        return DEFAULT_TEMPLATE;
    }

    private Path memoryFile(Integer userId, Long conversationId) {
        if (userId == null || userId <= 0 || conversationId == null || conversationId <= 0) {
            throw new IllegalArgumentException("语义记忆需要有效的用户 ID 和会话 ID");
        }
        Path file = storageRoot
                .resolve("user-" + userId)
                .resolve("conversation-" + conversationId)
                .resolve("MEMORY.md")
                .normalize();
        if (!file.startsWith(storageRoot)) {
            throw new IllegalArgumentException("非法的语义记忆路径");
        }
        return file;
    }

    private Object lockFor(Path file) {
        return fileLocks.computeIfAbsent(file, ignored -> new Object());
    }

    private void writeAtomically(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, content.strip() + System.lineSeparator(), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, file,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String cleanModelOutput(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String cleaned = value.trim();
        if (cleaned.startsWith("```")) {
            int firstLine = cleaned.indexOf('\n');
            int lastFence = cleaned.lastIndexOf("```");
            if (firstLine >= 0 && lastFence > firstLine) {
                cleaned = cleaned.substring(firstLine + 1, lastFence).trim();
            }
        }
        return cleaned;
    }

    private String limit(String value) {
        if (value == null) {
            return "";
        }
        int max = Math.max(1000, properties.getSemanticMemory().getMaxChars());
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String limitInput(String value) {
        if (value == null) {
            return "";
        }
        int max = 6000;
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
