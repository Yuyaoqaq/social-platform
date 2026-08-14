package com.cy.share.ai.agent.memory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * AI 会话实体，映射 ai_conversation 表，记录每次 Agent 对话的会话 ID、用户、标题和增量摘要。
 */
@Data
@TableName("ai_conversation")
public class AiConversation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer userId;
    private String title;
    private String summary;
    private Long summaryMessageId;
    private String status;
    private Date createTime;
    private Date updateTime;
}
