package com.cy.share.ai.agent.mcp;

import com.cy.share.ai.agent.config.AgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** 最小化 MCP stdio 客户端，负责启动、握手和调用本地 MCP Server。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpStdioClient {

    private static final String PROTOCOL_VERSION = "2025-06-18";

    private final AgentProperties properties;
    private final ObjectMapper objectMapper;
    private final Object lifecycleLock = new Object();
    private final Object writeLock = new Object();
    private final AtomicLong requestIds = new AtomicLong();
    private final ConcurrentHashMap<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();

    private volatile Process process;
    private volatile BufferedWriter writer;
    private volatile boolean initialized;

    @PostConstruct
    public void initialize() {
        if (!properties.getMcp().isEnabled()) {
            return;
        }
        try {
            ensureStarted();
        } catch (Exception e) {
            log.warn("MCP startup failed; it will retry on first tool call", e);
        }
    }

    public JsonNode callTool(String name, Map<String, Object> arguments) throws Exception {
        ensureStarted();
        synchronized (lifecycleLock) {
            return request("tools/call", Map.of(
                    "name", name,
                    "arguments", arguments), properties.getMcp().getTimeoutSeconds());
        }
    }

    public boolean isEnabled() {
        return properties.getMcp().isEnabled();
    }

    private void ensureStarted() throws Exception {
        synchronized (lifecycleLock) {
            if (initialized && process != null && process.isAlive()) {
                return;
            }
            stopProcess();
            startProcess();
        }
    }

    private void startProcess() throws Exception {
        AgentProperties.Mcp config = properties.getMcp();
        Path projectPath = Path.of(config.getProjectPath()).toAbsolutePath().normalize();
        Path serverScript = projectPath.resolve(config.getServerScript()).normalize();
        if (!Files.isDirectory(projectPath) || !Files.isRegularFile(serverScript)) {
            throw new IllegalStateException("MCP 项目或入口不存在: " + serverScript);
        }

        ProcessBuilder builder = new ProcessBuilder(config.getNodeCommand(), serverScript.toString());
        builder.directory(projectPath.toFile());
        builder.environment().put("NODE_NO_WARNINGS", "1");
        process = builder.start();
        writer = new BufferedWriter(new OutputStreamWriter(
                process.getOutputStream(), StandardCharsets.UTF_8));
        startStdoutReader(process);
        startStderrReader(process);

        JsonNode initializeResult = request("initialize", Map.of(
                "protocolVersion", PROTOCOL_VERSION,
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", "share-ai", "version", "0.0.1")), 10);
        String negotiatedVersion = initializeResult.path("protocolVersion").asText();
        if (negotiatedVersion.isBlank()) {
            throw new IllegalStateException("MCP 初始化响应缺少 protocolVersion");
        }
        notifyServer("notifications/initialized", Map.of());

        JsonNode tools = request("tools/list", Map.of(), 10).path("tools");
        boolean found = false;
        for (JsonNode tool : tools) {
            if ("search_xhs_hot_note".equals(tool.path("name").asText())) {
                found = true;
                break;
            }
        }
        if (!found) {
            throw new IllegalStateException("MCP 未提供 search_xhs_hot_note 工具");
        }
        initialized = true;
        log.info("MCP connected. server={}, protocol={}",
                initializeResult.path("serverInfo").path("name").asText("unknown"), negotiatedVersion);
    }

    private JsonNode request(String method, Map<String, Object> params, int timeoutSeconds) throws Exception {
        long id = requestIds.incrementAndGet();
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pending.put(id, future);
        try {
            send(Map.of(
                    "jsonrpc", "2.0",
                    "id", id,
                    "method", method,
                    "params", params));
            return future.get(Math.max(1, timeoutSeconds), TimeUnit.SECONDS);
        } finally {
            pending.remove(id);
        }
    }

    private void notifyServer(String method, Map<String, Object> params) throws Exception {
        send(Map.of(
                "jsonrpc", "2.0",
                "method", method,
                "params", params));
    }

    private void send(Map<String, Object> message) throws Exception {
        String line = objectMapper.writeValueAsString(message);
        synchronized (writeLock) {
            if (writer == null) {
                throw new IllegalStateException("MCP 进程未启动");
            }
            writer.write(line);
            writer.newLine();
            writer.flush();
        }
    }

    private void startStdoutReader(Process currentProcess) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    currentProcess.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    handleMessage(line);
                }
                if (process == currentProcess) {
                    failPending(new IllegalStateException("MCP stdout 已关闭"));
                }
            } catch (Exception e) {
                if (process == currentProcess) {
                    failPending(e);
                }
            } finally {
                if (process == currentProcess) {
                    initialized = false;
                }
            }
        }, "xhs-mcp-stdout");
        thread.setDaemon(true);
        thread.start();
    }

    private void startStderrReader(Process currentProcess) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    currentProcess.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("xhs-mcp: {}", line);
                }
            } catch (Exception e) {
                log.debug("MCP stderr reader stopped", e);
            }
        }, "xhs-mcp-stderr");
        thread.setDaemon(true);
        thread.start();
    }

    private void handleMessage(String line) {
        try {
            JsonNode message = objectMapper.readTree(line);
            if (!message.has("id")) {
                return;
            }
            CompletableFuture<JsonNode> future = pending.get(message.path("id").asLong());
            if (future == null) {
                return;
            }
            if (message.has("error")) {
                future.completeExceptionally(new IllegalStateException(
                        "MCP 调用失败: " + message.path("error").toString()));
            } else {
                future.complete(message.path("result"));
            }
        } catch (Exception e) {
            log.warn("Invalid MCP message: {}", abbreviate(line, 300), e);
        }
    }

    private void failPending(Exception error) {
        pending.values().forEach(future -> future.completeExceptionally(error));
        pending.clear();
    }

    private String abbreviate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max) + "...";
    }

    @PreDestroy
    public void close() {
        synchronized (lifecycleLock) {
            stopProcess();
        }
    }

    private void stopProcess() {
        initialized = false;
        BufferedWriter currentWriter = writer;
        writer = null;
        if (currentWriter != null) {
            try {
                currentWriter.close();
            } catch (Exception ignored) {
                // 关闭失败时继续销毁子进程。
            }
        }
        Process currentProcess = process;
        process = null;
        if (currentProcess != null && currentProcess.isAlive()) {
            currentProcess.destroy();
        }
        failPending(new IllegalStateException("MCP 连接已关闭"));
    }
}
