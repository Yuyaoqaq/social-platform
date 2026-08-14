package com.cy.share.ai.agent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图片生成结果 DTO，包含生成任务 ID、最终可访问的图片 URL、使用的模型和生成提示词。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageGenerationResult {
    private String taskId;
    private String imageUrl;
    private String model;
    private String prompt;
}
