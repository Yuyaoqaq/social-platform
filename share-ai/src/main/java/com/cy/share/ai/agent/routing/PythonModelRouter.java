package com.cy.share.ai.agent.routing;

import com.cy.share.ai.agent.config.AgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** 调用 bailian_router.py，为每轮 Agent 对话选择模型。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PythonModelRouter {

    private final AgentProperties properties;
    private final ObjectMapper objectMapper;

    private Path scriptPath;

    @PostConstruct
    public void initialize() {
        scriptPath = resolveScriptPath(properties.getRouting().getScriptPath());
        if (scriptPath == null) {
            log.warn("Python model router script not found; Agent will use fallback model");
        } else {
            log.info("Python model router initialized. script={}", scriptPath);
        }
    }

    public ModelRouteDecision route(String userMessage) {
        AgentProperties.Routing config = properties.getRouting();
        if (!config.isEnabled() || scriptPath == null) {
            return fallback("routing_disabled");
        }

        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    config.getPythonCommand(),
                    scriptPath.toString(),
                    "--dry-run",
                    "--stdin");
            builder.redirectErrorStream(true);
            builder.environment().put("PYTHONIOENCODING", "utf-8");
            builder.environment().put("BAILIAN_SIMPLE_MODEL", config.getSimpleModel());
            builder.environment().put("BAILIAN_COMPLEX_MODEL", config.getComplexModel());
            builder.environment().put("BAILIAN_SIMPLE_MAX_TOKENS",
                    String.valueOf(config.getSimpleMaxTokens()));
            builder.environment().put("BAILIAN_COMPLEX_MAX_TOKENS",
                    String.valueOf(config.getComplexMaxTokens()));

            process = builder.start();
            try (var stdin = process.getOutputStream()) {
                stdin.write(userMessage.getBytes(StandardCharsets.UTF_8));
            }

            boolean completed = process.waitFor(
                    Math.max(1, config.getTimeoutSeconds()), TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                log.warn("Python model routing timed out");
                return fallback("routing_timeout");
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                log.warn("Python model routing failed. exitCode={}, output={}",
                        process.exitValue(), abbreviate(output, 300));
                return fallback("routing_failed");
            }

            JsonNode json = objectMapper.readTree(output);
            String model = json.path("model").asText();
            if (model.isBlank()) {
                return fallback("routing_model_empty");
            }
            return new ModelRouteDecision(
                    json.path("intent").asText("general_request"),
                    json.path("simple").asBoolean(false),
                    json.path("complexity_score").asInt(0),
                    model,
                    positiveMaxTokens(json.path("max_tokens").asInt(), config),
                    json.path("requires_tools").asBoolean(false),
                    false);
        } catch (Exception e) {
            log.warn("Python model routing error; using fallback model", e);
            return fallback("routing_error");
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private ModelRouteDecision fallback(String intent) {
        AgentProperties.Routing config = properties.getRouting();
        return new ModelRouteDecision(
                intent,
                false,
                0,
                properties.getModel(),
                Math.max(256, config.getComplexMaxTokens()),
                false,
                true);
    }

    private int positiveMaxTokens(int value, AgentProperties.Routing config) {
        int fallback = Math.max(256, config.getComplexMaxTokens());
        return value <= 0 ? fallback : Math.min(value, 8192);
    }

    private Path resolveScriptPath(String configuredPath) {
        List<String> candidates = List.of(
                configuredPath == null ? "" : configuredPath,
                "bailian_router.py",
                "share-ai/bailian_router.py",
                "../share-ai/bailian_router.py");
        for (String candidate : candidates) {
            if (candidate.isBlank()) {
                continue;
            }
            Path path = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }

    private String abbreviate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...";
    }
}
