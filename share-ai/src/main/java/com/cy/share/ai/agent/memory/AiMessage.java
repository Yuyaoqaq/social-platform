package com.cy.share.ai.agent.memory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * AI 消息实体，映射 ai_message 表，记录对话中的每条 USER/ASSISTANT/TOOL 消息及工具调用元数据。
 */
@Data
@TableName("ai_message")
public class AiMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private Integer userId;
    private String role;
    private String content;
    private String toolName;
    private String toolStatus;
    private String metadataJson;
    private Date createTime;
}
