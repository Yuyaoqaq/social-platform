export interface AgentChatRequest {
  conversationId?: number
  message: string
  enableWebSearch: boolean
  enableImageGeneration: boolean
}

export interface AgentDraft {
  title?: string
  content?: string
  tags?: string[]
  images?: string[]
}

export interface AgentSource { type?: string; title?: string; url?: string; source?: string }
export interface AgentAnswer {
  conversationId: number
  status: string
  answer: string
  recommendations?: string[]
  draft?: AgentDraft
  sources?: AgentSource[]
  warnings?: string[]
}

export interface AgentConversation { id: number; title: string; summary?: string; status: string; updateTime: string }
export interface AgentMessage { id: number; conversationId: number; role: string; content: string; toolName?: string; toolStatus?: string; createTime: string }
export interface AgentStreamEvent { event: string; data: unknown }

export interface AgentTrace {
  traceId: string
  conversationId?: number
  status: string
  model?: string
  promptTokens: number
  completionTokens: number
  totalTokens: number
  durationMs: number
  errorMessage?: string
  startTime: string
  endTime?: string
}

export interface AgentTraceSpan {
  id: number
  sequenceNo: number
  spanType: 'WORKFLOW' | 'MEMORY' | 'RAG' | 'TOOL' | 'LLM'
  spanName: string
  status: string
  durationMs: number
  promptTokens: number
  completionTokens: number
  totalTokens: number
  detailJson?: string
  createTime: string
}

export interface AgentTraceDetail { trace: AgentTrace; spans: AgentTraceSpan[] }
