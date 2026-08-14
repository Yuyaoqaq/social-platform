package com.cy.share.ai.agent.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 生成的图文草稿 DTO，包含标题、正文、标签和配图 URL 列表。
 */
@Data
public class AgentDraft {
    private String title;
    private String content;
    private List<String> tags = new ArrayList<>();
    private List<String> images = new ArrayList<>();
}
