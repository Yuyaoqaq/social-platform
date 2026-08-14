package com.cy.share.ai.agent.rag;

import com.cy.share.ai.agent.config.AgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 知识自动索引启动器，在应用启动时若 rag.enabled 且 rag.autoIndex 为 true 则自动执行全文索引入库。
 * 索引失败不阻塞应用启动，仅记录警告日志。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeAutoIndexer implements ApplicationRunner {//启动后跑一段逻辑的代码

    private final AgentProperties properties;
    private final KnowledgeIngestService ingestService;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.getRag().isEnabled() || !properties.getRag().isAutoIndex()) {
            return;
        }
        try {
            KnowledgeIngestResult result = ingestService.reindex();
            log.info("Knowledge indexed. files={}, chunks={}", result.files(), result.chunks());
        } catch (Exception e) {
            log.warn("Knowledge auto index failed, Agent will continue without RAG", e);
        }
    }
}
