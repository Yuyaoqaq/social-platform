package com.cy.share.api;

import com.cy.share.dto.AiTagRequest;
import com.cy.share.dto.AiTagResult;
import java.util.concurrent.CompletableFuture;

public interface AiTagService {
    /**
     * 异步 AI 打标。fire-and-forget，不阻塞博客发布流程。
     */
    CompletableFuture<AiTagResult> tagBlog(AiTagRequest request);
}
