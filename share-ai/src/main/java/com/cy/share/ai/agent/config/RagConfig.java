package com.cy.share.ai.agent.config;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 上下文增强器配置，构造将知识库检索结果（或空结果）与用户问题拼接为 LLM 提示词的模板。
 */
@Configuration
public class RagConfig {
    //上下文查询增强器：把检索到的文档（或空结果）和用户问题，按照指定模板拼成一段提示词，喂给 AI。
    @Bean
    public ContextualQueryAugmenter contextualQueryAugmenter() {
        PromptTemplate prompt = new PromptTemplate("""
                下面是知识库检索结果：
                ---------------------
                {context}
                ---------------------
                请结合知识库回答。必须区分知识库事实和模型建议，并保留来源。
                用户问题：{query}
                """);
        PromptTemplate empty = new PromptTemplate("""
                知识库没有检索到与问题相关的规则或案例。
                不要声称知识库提供了答案。如果其他工具有数据，可以继续完成内容分析，并提示本次未使用知识库资料。
                用户问题：{query}
                """);
        //String finalPrompt = augmenter.augment(query, docs);时传入
        return ContextualQueryAugmenter.builder()
                .allowEmptyContext(false)
                .emptyContextPromptTemplate(empty)
                .promptTemplate(prompt)
                .build();
    }
}
