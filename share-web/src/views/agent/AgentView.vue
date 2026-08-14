<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showFailToast, showSuccessToast } from 'vant'
import { getAgentConversations, getAgentMessages, streamAgentChat } from '@/api/agent'
import type { AgentAnswer, AgentConversation, AgentMessage, AgentStreamEvent } from '@/types/agent'

interface ChatEntry { role: 'USER' | 'ASSISTANT'; content: string; answer?: AgentAnswer }
const router = useRouter()
const conversations = ref<AgentConversation[]>([])
const conversationId = ref<number>()
const entries = ref<ChatEntry[]>([])
const message = ref('')
const enableWebSearch = ref(true)
const enableImageGeneration = ref(false)
const running = ref(false)
const progress = ref('')
const scroller = ref<HTMLElement>()
let controller: AbortController | undefined

const scrollBottom = async () => { await nextTick(); scroller.value?.scrollTo({ top: scroller.value.scrollHeight, behavior: 'smooth' }) }
const loadConversations = async () => {
  try { const result = await getAgentConversations(); if (result.code === 200) conversations.value = result.data || [] } catch { /* 历史不可用不影响新对话 */ }
}
const openConversation = async (item: AgentConversation) => {
  if (running.value) return
  try {
    const result = await getAgentMessages(item.id)
    if (result.code !== 200) return showFailToast(result.msg || '会话加载失败')
    conversationId.value = item.id
    entries.value = (result.data || []).filter((msg: AgentMessage) => !msg.toolName).map((msg: AgentMessage) => ({ role: msg.role === 'USER' ? 'USER' : 'ASSISTANT', content: msg.content }))
    await scrollBottom()
  } catch { showFailToast('会话加载失败') }
}
const newConversation = () => { if (!running.value) { conversationId.value = undefined; entries.value = []; progress.value = '' } }

const handleEvent = (streamEvent: AgentStreamEvent) => {
  if (streamEvent.event === 'workflow_started') progress.value = '正在理解你的需求'
  if (streamEvent.event === 'tool_started') progress.value = `正在调用 ${(streamEvent.data as { name?: string }).name || '工具'}`
  if (streamEvent.event === 'tool_completed') progress.value = '资料已准备，正在整理内容'
  if (streamEvent.event === 'workflow_failed') throw new Error((streamEvent.data as { message?: string }).message || 'Agent 执行失败')
  if (streamEvent.event === 'workflow_completed') {
    const answer = streamEvent.data as AgentAnswer
    conversationId.value = answer.conversationId
    entries.value.push({ role: 'ASSISTANT', content: answer.answer, answer })
    progress.value = ''
    loadConversations()
    scrollBottom()
  }
}

const send = async () => {
  const content = message.value.trim()
  if (!content || running.value) return
  entries.value.push({ role: 'USER', content })
  message.value = ''
  running.value = true
  progress.value = '正在连接运营助手'
  controller = new AbortController()
  await scrollBottom()
  try {
    await streamAgentChat({ conversationId: conversationId.value, message: content, enableWebSearch: enableWebSearch.value, enableImageGeneration: enableImageGeneration.value }, handleEvent, controller.signal)
  } catch (error) {
    if ((error as Error).name !== 'AbortError') showFailToast(error instanceof Error ? error.message : 'Agent 执行失败')
  } finally { running.value = false; progress.value = ''; controller = undefined }
}
const stop = () => { controller?.abort(); running.value = false; progress.value = '已停止生成' }
const copyDraft = async (answer: AgentAnswer) => {
  const draft = answer.draft
  if (!draft) return
  await navigator.clipboard.writeText([draft.title, draft.content, draft.tags?.map((tag) => `#${tag}`).join(' ')].filter(Boolean).join('\n\n'))
  showSuccessToast('草稿已复制')
}
onMounted(loadConversations)
</script>

<template>
  <main class="agent-page">
    <header><button type="button" aria-label="返回首页" @click="router.push('/home/index')"><van-icon name="arrow-left" size="24" /></button><div><strong>AI 内容运营助手</strong><span>搜索、分析并生成可引用的内容草稿</span></div><button type="button" aria-label="查看链路" @click="router.push('/agent/traces')"><van-icon name="chart-trending-o" size="24" /></button></header>
    <div class="workspace">
      <aside><h2>历史会话</h2><button type="button" class="new-chat" @click="newConversation"><van-icon name="add-o" /> 新对话</button><button v-for="item in conversations" :key="item.id" type="button" :class="{ active: conversationId === item.id }" @click="openConversation(item)"><strong>{{ item.title || '未命名会话' }}</strong><span>{{ item.summary || item.status }}</span></button></aside>
      <section class="chat-panel">
        <div ref="scroller" class="messages" aria-live="polite">
          <div v-if="!entries.length" class="welcome"><span class="assistant-mark"><van-icon name="chat-o" size="28" /></span><h1>今天想运营什么内容？</h1><p>我可以搜索站内内容、补充外部主题、参考运营规则，并生成标题、正文和标签。</p><div class="examples"><button v-for="example in ['分析最近的露营内容并生成合集文案', '根据站内热门内容给我 3 个选题', '生成一篇适合新手的养猫清单']" :key="example" type="button" @click="message = example">{{ example }}</button></div></div>
          <article v-for="(entry, index) in entries" :key="index" :class="['message', entry.role.toLowerCase()]">
            <p>{{ entry.content }}</p>
            <template v-if="entry.answer?.draft"><section class="draft-card"><span>内容草稿</span><h3>{{ entry.answer.draft.title }}</h3><p>{{ entry.answer.draft.content }}</p><div class="draft-tags"><span v-for="tag in entry.answer.draft.tags" :key="tag">#{{ tag }}</span></div><button type="button" @click="copyDraft(entry.answer)"><van-icon name="description" /> 复制草稿</button></section></template>
            <details v-if="entry.answer?.sources?.length"><summary>查看 {{ entry.answer.sources.length }} 条引用来源</summary><a v-for="source in entry.answer.sources" :key="source.url || source.title" :href="source.url" target="_blank" rel="noreferrer">{{ source.title || source.source }}</a></details>
            <p v-for="warning in entry.answer?.warnings" :key="warning" class="warning">{{ warning }}</p>
          </article>
          <div v-if="running" class="progress"><van-loading size="18" /> {{ progress }}</div>
        </div>
        <footer>
          <div class="options"><van-switch v-model="enableWebSearch" size="18" /><span>联网搜索</span><van-switch v-model="enableImageGeneration" size="18" /><span>生成配图</span></div>
          <form @submit.prevent="send"><textarea v-model="message" rows="2" maxlength="4000" aria-label="给运营助手发送消息" placeholder="描述你的内容运营需求…" @keydown.enter.exact.prevent="send" /><button v-if="running" type="button" aria-label="停止生成" @click="stop"><van-icon name="pause-circle-o" /></button><button v-else type="submit" aria-label="发送消息" :disabled="!message.trim()"><van-icon name="guide-o" /></button></form>
        </footer>
      </section>
    </div>
  </main>
</template>

<style scoped>
.agent-page { height: 100vh; height: 100dvh; background: #f3f9fa; }
header { display: grid; grid-template-columns: 44px 1fr 44px; align-items: center; gap: 10px; min-height: 66px; padding: max(10px, env(safe-area-inset-top)) 14px 10px; border-bottom: 1px solid var(--share-border); background: white; }
header button { width: 44px; height: 44px; border: 0; border-radius: 12px; background: transparent; cursor: pointer; } header div { display: flex; flex-direction: column; } header span { color: var(--share-muted); font-size: 11px; }
.workspace { display: grid; height: calc(100% - 66px); max-width: 1100px; margin: 0 auto; }
aside { display: none; padding: 18px 14px; overflow-y: auto; border-right: 1px solid var(--share-border); background: white; } aside h2 { margin: 0 8px 12px; font-size: 15px; } aside button { display: flex; width: 100%; min-height: 48px; flex-direction: column; justify-content: center; margin-bottom: 6px; padding: 8px 10px; border: 0; border-radius: 10px; color: var(--share-text); background: transparent; text-align: left; cursor: pointer; } aside button.active { background: #e7f6f8; } aside button span { overflow: hidden; color: var(--share-muted); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; } aside .new-chat { align-items: center; flex-direction: row; gap: 6px; border: 1px solid var(--share-border); color: var(--share-primary-dark); }
.chat-panel { display: grid; min-width: 0; grid-template-rows: 1fr auto; overflow: hidden; }
.messages { overflow-y: auto; padding: 20px 14px 30px; }
.welcome { max-width: 620px; margin: 8vh auto 0; text-align: center; } .assistant-mark { display: grid; place-items: center; width: 58px; height: 58px; margin: 0 auto; border-radius: 18px; color: white; background: var(--share-primary); } .welcome h1 { margin: 18px 0 6px; } .welcome p { color: var(--share-muted); } .examples { display: grid; gap: 8px; margin-top: 24px; } .examples button { min-height: 46px; padding: 8px 12px; border: 1px solid var(--share-border); border-radius: 12px; color: #31505a; background: white; cursor: pointer; }
.message { max-width: 680px; margin: 0 auto 18px; } .message > p { margin: 0; padding: 13px 15px; border-radius: 16px; white-space: pre-wrap; } .message.user > p { width: fit-content; max-width: 88%; margin-left: auto; color: white; background: var(--share-primary-dark); } .message.assistant > p { border: 1px solid var(--share-border); background: white; }
.draft-card { margin-top: 10px; padding: 16px; border: 1px solid #a9d5dc; border-radius: 16px; background: #f5fcfd; } .draft-card > span { color: var(--share-primary-dark); font-size: 12px; font-weight: 800; } .draft-card h3 { margin: 8px 0; } .draft-card p { white-space: pre-wrap; } .draft-tags { display: flex; gap: 6px; flex-wrap: wrap; color: var(--share-primary-dark); font-size: 12px; } .draft-card button { min-height: 40px; margin-top: 14px; padding: 0 12px; border: 1px solid var(--share-border); border-radius: 10px; color: var(--share-primary-dark); background: white; cursor: pointer; }
details { margin-top: 8px; padding: 10px 14px; color: var(--share-muted); font-size: 13px; } details a { display: block; margin-top: 6px; color: var(--share-primary-dark); } .warning { margin-top: 6px !important; color: #92400e; background: #fff7ed !important; }
.progress { display: flex; max-width: 680px; align-items: center; gap: 8px; margin: 0 auto; color: var(--share-muted); font-size: 13px; }
.chat-panel > footer { padding: 10px 14px max(14px, env(safe-area-inset-bottom)); border-top: 1px solid var(--share-border); background: white; } .options { display: flex; align-items: center; gap: 7px; max-width: 680px; margin: 0 auto 8px; color: var(--share-muted); font-size: 12px; } .options span + .van-switch { margin-left: 10px; } form { display: grid; grid-template-columns: 1fr 46px; max-width: 680px; align-items: end; gap: 8px; margin: 0 auto; padding: 8px 8px 8px 14px; border: 1px solid var(--share-border); border-radius: 18px; background: #f8fbfc; } textarea { resize: none; border: 0; outline: 0; background: transparent; } form button { width: 44px; height: 44px; border: 0; border-radius: 13px; color: white; background: var(--share-primary); cursor: pointer; } form button:disabled { background: #a8bec3; cursor: not-allowed; }
@media (min-width: 820px) { .workspace { grid-template-columns: 250px 1fr; } aside { display: block; } .messages { padding-inline: 28px; } }
</style>
