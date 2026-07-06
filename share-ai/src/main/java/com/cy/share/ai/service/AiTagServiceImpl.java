package com.cy.share.ai.service;

import com.cy.share.ai.mapper.LogTagMapper;
import com.cy.share.ai.mq.TagSyncProducer;
import com.cy.share.api.AiTagService;
import com.cy.share.dto.AiTagRequest;
import com.cy.share.dto.AiTagResult;
import com.cy.share.pojo.LogTag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.MimeTypeUtils;

import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@DubboService
@RequiredArgsConstructor
public class AiTagServiceImpl implements AiTagService {

    private final LogTagMapper logTagMapper;
    private final TagSyncProducer tagSyncProducer;
    private final ChatClient chatClient;
    private final TransactionTemplate transactionTemplate;

    @Override
    public CompletableFuture<AiTagResult> tagBlog(AiTagRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<String> tags = doTagging(request);
                if (tags != null && !tags.isEmpty()) {
                    persistTags(request.getLogId(), tags);
                }
                return new AiTagResult(request.getLogId(), tags);
            } catch (Exception e) {
                log.error("AI tagging failed for logId={}", request.getLogId(), e);
                return new AiTagResult(request.getLogId(), Collections.emptyList());
            }
        });
    }

    private List<String> doTagging(AiTagRequest request) {
        String prompt = "根据内容生成1-3个中文标签，逗号分隔，只输出标签，不要其他内容。";

        String response;
        if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            // 图片优先：图文结合
            String fullPrompt = prompt + "\n标题：" + request.getTitle();
            try {
                response = chatClient.prompt()
//                        .system("""
//                                你是ins风格的标签生成专家。
//                                请从以下平台标签库中选择最匹配的 1-3 个标签：
//                                {tagLibrary}
//                                禁止生成：政治、色情、暴力相关标签。
//                                输出格式：只返回 JSON 数组，如 ["穿搭", "OOTD", "显瘦"]
//                                """)
                        .user(u -> {
                            u.text(fullPrompt);
                            for (String imageUrl : request.getImageUrls()) {
                                try {
                                    u.media(MimeTypeUtils.IMAGE_PNG, URI.create(imageUrl).toURL());
                                } catch (java.net.MalformedURLException ignored) {
                                    // skip malformed URL
                                }
                            }
                        })
                        .call()
                        .content();
            } catch (Exception e) {
                log.warn("Visual tagging failed, fallback to text. logId={}", request.getLogId());
                response = textOnlyTag(request, prompt);
            }
        } else {
            response = textOnlyTag(request, prompt);
        }
        return parseTags(response);
    }

    private String textOnlyTag(AiTagRequest request, String prompt) {
        String fullPrompt = prompt
                + "\n标题：" + request.getTitle()
                + "\n内容：" + (request.getInfo() != null ? request.getInfo() : "");
        return chatClient.prompt().user(fullPrompt).call().content();
    }

    private List<String> parseTags(String response) {
        if (response == null || response.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(response.split("[,，]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .limit(3)//取前三段
                .collect(Collectors.toList());
    }

    private void persistTags(Integer logId, List<String> tags) {
        transactionTemplate.executeWithoutResult(status -> {
            for (String tag : tags) {
                LogTag logTag = new LogTag();
                logTag.setLogId(logId.longValue());
                logTag.setTagName(tag);
                logTagMapper.insert(logTag);
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    tagSyncProducer.sendBlogTag(logId, tags);
                }
            });
        });
    }
}
