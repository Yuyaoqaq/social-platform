package com.cy.share.ai.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent 配置属性类，绑定 application.yml 中 agent.* 前缀的所有配置项，
 * 包括模型选择、工具调用步数/超时、RAG 参数、外部搜索和图片生成配置。
 */
@Data
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    private int maxToolSteps = 5;
    private int toolTimeoutSeconds = 10;
    private int maxContextTokens = 32768;
    private double memoryCompressRatio = 0.5;
    private int rateLimitPerMinute = 20;
    private int imageLimitPerDay = 5;
    private String model = "qwen3.7-plus";
    private Routing routing = new Routing();
    private SemanticMemory semanticMemory = new SemanticMemory();
    private Mcp mcp = new Mcp();
    private Rag rag = new Rag();
    private WebSearch webSearch = new WebSearch();
    private Image image = new Image();

    @Data
    public static class Routing {
        private boolean enabled = true;
        private String pythonCommand = "python";
        private String scriptPath = "bailian_router.py";
        private int timeoutSeconds = 3;
        private String simpleModel = "qwen-turbo";
        private String complexModel = "qwen3.7-plus";
        private int simpleMaxTokens = 512;
        private int complexMaxTokens = 2048;
    }

    @Data
    public static class SemanticMemory {
        private boolean enabled = true;
        private String storagePath = "../semantic-memory";
        private String templatePath = "../MEMORY.md";
        private int maxChars = 8000;
    }

    @Data
    public static class Mcp {
        private boolean enabled = true;
        private String nodeCommand = "node";
        private String projectPath = "D:/JAVAfilesAll/cyymcp";
        private String serverScript = "dist/index.js";
        private int timeoutSeconds = 120;
        private boolean headless = false;
        private boolean downloadImages = true;
        private String outputDir = "";
    }

    @Data
    public static class Rag {
        private boolean enabled = true;
        private boolean autoIndex = false;
        private String knowledgePath = "../knowledge";
        private String qdrantUrl = "http://192.168.175.18:6333";
        private String collection = "share_operation_knowledge_v2";
        private int vectorSize = 1024;
        private String distance = "Cosine";
        private int hybridCandidateK = 20;
        private int chunkSize = 1200;
        private int chunkOverlap = 120;
    }

    @Data
    public static class WebSearch {
        private boolean enabled = true;
        private String url = "https://www.searchapi.io/api/v1/search";
        private String apiKey;
        private String engine = "baidu";
        private int cacheMinutes = 30;
    }

    @Data
    public static class Image {
        private boolean enabled = true;
        private String apiKey;
        private String workspaceId;
        private String submitUrl;
        private String model = "qwen-image-2.0-pro-2026-06-22";
        private int timeoutSeconds = 120;
    }
}
