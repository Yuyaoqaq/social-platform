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

    private static final String SUMMARY_SYSTEM_PROMPT = """
            增量更新任务摘要。请合并已有摘要和新增对话，输出下面的固定结构：

            ## 用户目标
            ## 成功标准
            ## 已确认事实
            ## 禁止事项
            ## 用户偏好
            ## 关键决定
            ## 已完成进度
            ## 未完成任务
            ## 待确认问题

            摘要规则：
            1. 最新的用户要求优先于旧要求；删除已失效、冲突或被用户否定的内容。
            2. 禁止事项、任务边界和用户明确说“不做”的内容必须保留。
            3. 只有用户确认、工具验证或已有结果明确支持的内容，才能写入“已确认事实”。
            4. 助手提出但用户未接受的建议，不能写成关键决定。
            5. 已完成进度只记录结果、关键标识和文件位置，不记录冗长过程。
            6. 未完成任务按优先级记录；存在阻塞时写入待确认问题。
            7. 没有内容的栏目写“无”。
            8. 不要添加新信息，不要保存推测、内部思考、客套话和无关细节。
            9. 摘要总长度严格控制在 800 字以内；超长时压缩各栏目措辞、删除次要细节，但禁止事项、关键决定、未完成任务必须保留。
            """;

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

    // 加载摘要之后尚未压缩的原始对话
    public MemoryContext load(Long conversationId, Integer userId) {
        AiConversation conversation = conversationMapper.selectOne(new LambdaQueryWrapper<AiConversation>()
                .eq(AiConversation::getId, conversationId)
                .eq(AiConversation::getUserId, userId));
        if (conversation == null) {
            throw new IllegalArgumentException("会话不存在或不属于当前用户");
        }
        List<AiMessage> messages = unsummarizedDialogueMessages(conversation);
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

    // 未压缩记忆达到上下文预算的一定比例后，将较早消息合并进摘要
    public void summarizeIfNeeded(Long conversationId, Integer userId) {
        AiConversation conversation = conversationMapper.selectOne(new LambdaQueryWrapper<AiConversation>()
                .eq(AiConversation::getId, conversationId)
                .eq(AiConversation::getUserId, userId));
        if (conversation == null) {
            return;
        }

        List<AiMessage> unsummarizedMessages = unsummarizedDialogueMessages(conversation);
        if (unsummarizedMessages.isEmpty()) {
            return;
        }

        int compressThreshold = compressThresholdTokens();
        int memoryTokens = estimateTokens(conversation.getSummary());
        for (AiMessage message : unsummarizedMessages) {
            memoryTokens += estimateMessageTokens(message);
        }
        if (memoryTokens < compressThreshold) {
            return;
        }

        // 触发后保留约 1/4 上下文的最近原始消息，避免下一轮立刻再次压缩
        int retainedTokenBudget = Math.max(256, compressThreshold / 2);
        int firstRetainedIndex = firstRetainedIndex(unsummarizedMessages, retainedTokenBudget);
        if (firstRetainedIndex <= 0) {
            return;
        }
        List<AiMessage> messagesToSummarize = unsummarizedMessages.subList(0, firstRetainedIndex);

        String previousSummary = conversation.getSummary();
        StringBuilder summaryInput = new StringBuilder();
        if (previousSummary != null && !previousSummary.isBlank()) {
            summaryInput.append("已有摘要：\n").append(previousSummary).append("\n\n");
        }
        summaryInput.append("本次需要合并的较早对话：\n");
        for (AiMessage message : messagesToSummarize) {
            summaryInput.append(message.getRole())
                    .append(": ")
                    .append(message.getContent())
                    .append('\n');
        }

        long newSummaryMessageId = messagesToSummarize.get(messagesToSummarize.size() - 1).getId();
        try {
            String summary = chatClient.prompt()
                    .system(SUMMARY_SYSTEM_PROMPT)
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

    private List<AiMessage> unsummarizedDialogueMessages(AiConversation conversation) {
        LambdaQueryWrapper<AiMessage> query = new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversation.getId())
                .in(AiMessage::getRole, "USER", "ASSISTANT")
                .orderByAsc(AiMessage::getId);
        if (conversation.getSummaryMessageId() != null) {
            query.gt(AiMessage::getId, conversation.getSummaryMessageId());
        }
        return messageMapper.selectList(query);
    }

    private int compressThresholdTokens() {
        int maxContextTokens = Math.max(1024, properties.getMaxContextTokens());
        double ratio = Math.max(0.1, Math.min(0.9, properties.getMemoryCompressRatio()));
        return Math.max(512, (int) Math.floor(maxContextTokens * ratio));
    }

    private int firstRetainedIndex(List<AiMessage> messages, int tokenBudget) {
        int retainedTokens = 0;
        int index = messages.size();
        while (index > 0) {
            int messageTokens = estimateMessageTokens(messages.get(index - 1));
            if (retainedTokens > 0 && retainedTokens + messageTokens > tokenBudget) {
                break;
            }
            retainedTokens += messageTokens;
            index--;
        }

        // 不把同一轮的 USER 和 ASSISTANT 从中间切开
        if (index > 0
                && index < messages.size()
                && "ASSISTANT".equals(messages.get(index).getRole())
                && "USER".equals(messages.get(index - 1).getRole())) {
            index--;
        }
        return index;
    }

    private int estimateMessageTokens(AiMessage message) {
        return 4 + estimateTokens(message.getContent());
    }

    private int estimateTokens(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        int cjkCharacters = 0;
        int otherCharacters = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            if (script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL) {
                cjkCharacters++;
            } else if (!Character.isWhitespace(codePoint)) {
                otherCharacters++;
            }
            offset += Character.charCount(codePoint);
        }
        return cjkCharacters + (otherCharacters + 3) / 4;
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
