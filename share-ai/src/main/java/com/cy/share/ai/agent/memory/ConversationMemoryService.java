package com.cy.share.ai.agent.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cy.share.ai.agent.config.AgentProperties;
import com.cy.share.ai.agent.model.AgentToolCallRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 会话记忆管理服务，负责会话创建/加载、消息持久化、最近对话提取和基于 ChatClient 的增量摘要。
 * 在 Agent 对话流程中充当"长期记忆层"，管理上下文窗口之外的历史信息。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationMemoryService {

    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final AgentProperties properties;
    private final ChatClient chatClient;

    // “获取或创建” AI 对话会话（Conversation） 的服务入口
    @Transactional
    public AiConversation open(Integer userId, Long requestedId, String firstMessage) {
        if (requestedId != null) {
            AiConversation existing = conversationMapper.selectOne(new LambdaQueryWrapper<AiConversation>()
                    .eq(AiConversation::getId, requestedId)
                    .eq(AiConversation::getUserId, userId));
            if (existing == null) {
                throw new IllegalArgumentException("会话不存在或不属于当前用户");
            }
            return existing;
        }

        Date now = new Date();
        AiConversation conversation = new AiConversation();
        conversation.setUserId(userId);
        conversation.setTitle(abbreviate(firstMessage, 40));
        conversation.setStatus("ACTIVE");
        conversation.setCreateTime(now);
        conversation.setUpdateTime(now);
        conversationMapper.insert(conversation);
        return conversation;
    }

    //加载会话上下文，包括摘要和最近对话消息
    public MemoryContext load(Long conversationId, Integer userId) {
        AiConversation conversation = conversationMapper.selectOne(new LambdaQueryWrapper<AiConversation>()
                .eq(AiConversation::getId, conversationId)
                .eq(AiConversation::getUserId, userId));
        if (conversation == null) {
            throw new IllegalArgumentException("会话不存在或不属于当前用户");
        }
        //最近10轮对话消息（USER+ASSISTANT）
        List<AiMessage> messages = recentDialogueMessages(conversationId);
        //根据list下标顺序颠倒
        Collections.reverse(messages);
        return new MemoryContext(conversation.getSummary(), messages);
    }

    //保存一条消息
    public void saveMessage(Long conversationId, Integer userId, String role, String content) {
        AiMessage message = new AiMessage();
        message.setConversationId(conversationId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setCreateTime(new Date());
        messageMapper.insert(message);
        touch(conversationId);
    }

    //保存多条工具调用记录
    public void saveToolCalls(Long conversationId, Integer userId, List<AgentToolCallRecord> toolCalls) {
        for (AgentToolCallRecord call : toolCalls) {
            AiMessage message = new AiMessage();
            message.setConversationId(conversationId);
            message.setUserId(userId);
            message.setRole("TOOL");
            message.setContent(call.getResultSummary());
            message.setToolName(call.getToolName());
            message.setToolStatus(call.getStatus());
            message.setMetadataJson(call.getArguments());
            message.setCreateTime(new Date());
            messageMapper.insert(message);
        }
    }

    //返回会话列表，8条分页
    public MemoryPage<AiConversation> list(Integer userId, Integer page) {
        int safePage = normalizePage(page);
        LambdaQueryWrapper<AiConversation> condition = new LambdaQueryWrapper<AiConversation>()
                .eq(AiConversation::getUserId, userId);
        long total = conversationMapper.selectCount(condition);
        List<AiConversation> records = conversationMapper.selectList(new LambdaQueryWrapper<AiConversation>()
                .eq(AiConversation::getUserId, userId)
                .orderByDesc(AiConversation::getUpdateTime)
                .last(limitClause(safePage)));
        return new MemoryPage<>(records, total);
    }

    //返回消息列表，8条分页
    public MemoryPage<AiMessage> messages(Long conversationId, Integer userId, Integer page) {
        AiConversation conversation = conversationMapper.selectOne(new LambdaQueryWrapper<AiConversation>()
                .eq(AiConversation::getId, conversationId)
                .eq(AiConversation::getUserId, userId));
        if (conversation == null) {
            throw new IllegalArgumentException("会话不存在或不属于当前用户");
        }
        int safePage = normalizePage(page);
        long total = messageMapper.selectCount(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId));
        List<AiMessage> records = messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId)
                .orderByDesc(AiMessage::getId)
                .last(limitClause(safePage)));
        Collections.reverse(records);
        return new MemoryPage<>(records, total);
    }

    //如果会话消息数超过阈值，则进行增量摘要
    public void summarizeIfNeeded(Long conversationId, Integer userId) {
        //获取会话
        AiConversation conversation = conversationMapper.selectOne(new LambdaQueryWrapper<AiConversation>()
                .eq(AiConversation::getId, conversationId)
                .eq(AiConversation::getUserId, userId));
        if (conversation == null) {
            return;
        }

        //获取会话所有消息数（用户+AI）
        long dialogueCount = messageMapper.selectCount(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId)
                .in(AiMessage::getRole, "USER", "ASSISTANT"));
        int retainedMessageCount = Math.max(1, properties.getRecentRounds()) * 2;
        //增量摘要阈值
        int summaryThreshold = Math.max(properties.getSummaryThreshold(), retainedMessageCount + 1);
        if (dialogueCount < summaryThreshold) {
            return;
        }
        //获取最近10轮对话消息（用户+AI）
        List<AiMessage> recentMessages = recentDialogueMessages(conversationId);
        if (recentMessages.isEmpty()) {
            return;
        }
        //最近10轮对话消息的最早消息ID
        long firstRetainedMessageId = recentMessages.stream()
                .mapToLong(AiMessage::getId)
                .min()
                .orElse(Long.MAX_VALUE);
        //上一次摘要消息ID
        long summarizedThroughId = conversation.getSummaryMessageId() == null
                ? 0L
                : conversation.getSummaryMessageId();
        //未摘要的消息列表，即：消息ID > summarizedThroughId 且 < firstRetainedMessageId
        List<AiMessage> unsummarizedMessages = messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId)
                .in(AiMessage::getRole, "USER", "ASSISTANT")
                .gt(AiMessage::getId, summarizedThroughId)
                .lt(AiMessage::getId, firstRetainedMessageId)
                .orderByAsc(AiMessage::getId));
        if (unsummarizedMessages.isEmpty()) {
            return;
        }
        //old摘要
        String previousSummary = conversation.getSummary();
        //sb全部拼起来（old摘要+未摘要的消息）
        StringBuilder summaryInput = new StringBuilder();
        if (previousSummary != null && !previousSummary.isBlank()) {
            summaryInput.append("已有摘要：\n").append(previousSummary).append("\n\n");
        }
        summaryInput.append("本次需要合并的较早对话：\n");
        for (AiMessage message : unsummarizedMessages) {
            summaryInput.append(message.getRole())
                    .append(": ")
                    .append(abbreviate(message.getContent(), 1500))
                    .append('\n');
        }

        long newSummaryMessageId = unsummarizedMessages.get(unsummarizedMessages.size() - 1).getId();
        try {
            String summary = chatClient.prompt()
                    .system("增量更新多轮对话摘要。合并已有摘要和新增旧对话，只保留用户目标、已确认事实、偏好和未完成事项，不要添加新信息。")
                    .user(summaryInput.toString())
                    .call()
                    .content();
            //更新会话，覆盖旧摘要
            conversationMapper.update(null, new LambdaUpdateWrapper<AiConversation>()
                    .eq(AiConversation::getId, conversationId)
                    .eq(AiConversation::getUserId, userId)
                    .set(AiConversation::getSummary, summary)
                    .set(AiConversation::getSummaryMessageId, newSummaryMessageId)
                    .set(AiConversation::getUpdateTime, new Date()));
        } catch (Exception e) {
            log.warn("Conversation summary failed. conversationId={}", conversationId, e);
        }
    }

    //获取一个会话中最近 10 轮（用户提问 + AI 回复）的完整对话消息列表
    private List<AiMessage> recentDialogueMessages(Long conversationId) {
        int roundLimit = Math.max(1, properties.getRecentRounds());
        List<AiMessage> recentUserMessages = messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId)
                .eq(AiMessage::getRole, "USER")
                .orderByDesc(AiMessage::getId)
                .last("LIMIT " + roundLimit));
        if (recentUserMessages.isEmpty()) {
            return Collections.emptyList();
        }
        //找出最早的用户消息ID
        long firstRetainedUserMessageId = recentUserMessages.stream()
                .mapToLong(AiMessage::getId)
                .min()
                .orElse(Long.MAX_VALUE);
        return messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId)
                .in(AiMessage::getRole, "USER", "ASSISTANT")
                .ge(AiMessage::getId, firstRetainedUserMessageId)
                .orderByDesc(AiMessage::getId));
    }

    // 更新会话的最后访问时间
    private void touch(Long conversationId) {
        conversationMapper.update(null, new LambdaUpdateWrapper<AiConversation>()
                .eq(AiConversation::getId, conversationId)
                .set(AiConversation::getUpdateTime, new Date()));
    }

    //缩写会话title
    private String abbreviate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...";
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private String limitClause(int page) {
        int pageSize = 8;
        int offset = (page - 1) * pageSize;
        return "LIMIT " + offset + ", " + pageSize;
    }
}
