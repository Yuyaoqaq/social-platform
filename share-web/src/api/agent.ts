import axios from 'axios'
import { TOKEN_STORAGE_KEY } from '@/api/http'
import type { ApiResult } from '@/types/api'
import type { AgentChatRequest, AgentConversation, AgentMessage, AgentStreamEvent, AgentTrace, AgentTraceDetail } from '@/types/agent'

const agentHttp = axios.create({ baseURL: import.meta.env.VITE_AGENT_BASE_URL, timeout: 15_000 })
agentHttp.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_STORAGE_KEY)
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
agentHttp.interceptors.response.use((response) => response.data)

export const getAgentConversations = (page = 1) =>
  agentHttp.get<never, ApiResult<AgentConversation[]>>('/agent/conversations', { params: { page } })

export const getAgentMessages = (conversationId: number, page = 1) =>
  agentHttp.get<never, ApiResult<AgentMessage[]>>(`/agent/conversations/${conversationId}/messages`, { params: { page } })

export const getAgentTraces = (page = 1, size = 20) =>
  agentHttp.get<never, ApiResult<AgentTrace[]>>('/agent/traces', { params: { page, size } })

export const getAgentTrace = (traceId: string) =>
  agentHttp.get<never, ApiResult<AgentTraceDetail>>(`/agent/traces/${traceId}`)

export async function streamAgentChat(
  request: AgentChatRequest,
  onEvent: (event: AgentStreamEvent) => void,
  signal?: AbortSignal,
) {
  const token = localStorage.getItem(TOKEN_STORAGE_KEY)
  const response = await fetch(`${import.meta.env.VITE_AGENT_BASE_URL}/agent/chat/stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token || ''}` },
    body: JSON.stringify(request),
    signal,
  })
  if (!response.ok) throw new Error(response.status === 401 ? '登录状态失效' : 'Agent 服务请求失败')
  if (!response.body) throw new Error('浏览器不支持流式响应')

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { value, done } = await reader.read()
    buffer += decoder.decode(value, { stream: !done }).replace(/\r\n/g, '\n')
    const blocks = buffer.split('\n\n')
    buffer = blocks.pop() || ''
    for (const block of blocks) {
      let event = 'message'
      const dataLines: string[] = []
      for (const line of block.split('\n')) {
        if (line.startsWith('event:')) event = line.slice(6).trim()
        if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
      }
      if (!dataLines.length) continue
      const raw = dataLines.join('\n')
      try { onEvent({ event, data: JSON.parse(raw) }) } catch { onEvent({ event, data: raw }) }
    }
    if (done) break
  }
}
