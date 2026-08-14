package com.cy.share.ai.agent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 回答中引用的信息来源 DTO，标识每条引用的类型、标题、URL 和来源名称。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentSource {
    private String type;
    private String title;
    private String url;
    private String source;
}
