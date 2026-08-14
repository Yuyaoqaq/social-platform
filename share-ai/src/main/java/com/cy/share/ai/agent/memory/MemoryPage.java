package com.cy.share.ai.agent.memory;

import java.util.List;

/**
 * 通用分页记录，用于会话列表和消息列表的分页查询返回值。
 */
public record MemoryPage<T>(List<T> records, long total) {
}
