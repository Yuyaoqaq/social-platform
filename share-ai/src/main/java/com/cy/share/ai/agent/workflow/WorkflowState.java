package com.cy.share.ai.agent.workflow;

/**
 * Agent 工作流状态枚举，标记一次对话请求从接收到完成/失败所经过的各个阶段。
 */
public enum WorkflowState {
    /*
        接收：请求刚进来，仅做了参数校验和会话创建，尚未开始任何业务处理
     */
    RECEIVED,
    /*
        记忆处理：正在打开会话、加载历史消息并保存当前用户消息
    */
    MEMORY,
    /*
        检索：正在调用 Qdrant 执行向量检索，从知识库召回相关文档片段
    */
    RETRIEVING,
    /*
       工具执行：正在执行外部工具调用，如天气API、搜索引擎、计算器等（对应ReAct的Action）
    */
    TOOL_EXECUTING,
    /*
       格式化：正在将检索到的文档、工具返回结果、历史对话拼装成大模型的提示词上下文
    */
    FORMATTING,
    /*
        持久化：大模型回复已生成，正在将对话记录写入数据库，并可能触发异步摘要压缩
    */
    PERSISTING,
}
