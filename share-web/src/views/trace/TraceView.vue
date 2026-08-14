<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showFailToast } from 'vant'
import { getAgentTrace, getAgentTraces } from '@/api/agent'
import type { AgentTrace, AgentTraceDetail, AgentTraceSpan } from '@/types/agent'

const router = useRouter()
const traces = ref<AgentTrace[]>([])
const selected = ref<AgentTraceDetail>()
const loading = ref(false)

const llmSpans = computed(() => selected.value?.spans.filter((span) => span.spanType === 'LLM') || [])
const maxDuration = computed(() => Math.max(1, ...(selected.value?.spans.map((span) => span.durationMs) || [1])))

const load = async () => {
  loading.value = true
  try {
    const result = await getAgentTraces()
    if (result.code !== 200) return showFailToast(result.msg || '链路加载失败')
    traces.value = result.data || []
    if (traces.value[0]) await selectTrace(traces.value[0])
  } catch { showFailToast('链路服务不可用') } finally { loading.value = false }
}

const selectTrace = async (trace: AgentTrace) => {
  try {
    const result = await getAgentTrace(trace.traceId)
    if (result.code === 200) selected.value = result.data
  } catch { showFailToast('链路详情加载失败') }
}

const label = (span: AgentTraceSpan) => {
  const names: Record<string, string> = {
    workflow_started: '请求开始', workflow_state: '工作流状态', memory_completed: '加载会话记忆',
    rag_completed: '检索运营知识', tool_started: '工具开始', tool_completed: '工具完成',
    model_completed: '模型调用', persistence_completed: '保存会话记录',
  }
  return names[span.spanName] || span.spanName
}
const formatTime = (ms: number) => ms >= 1000 ? `${(ms / 1000).toFixed(2)}s` : `${ms}ms`
const formatDate = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false })
const typeIcon = (type: string) => ({ LLM: 'chat-o', TOOL: 'setting-o', RAG: 'search', MEMORY: 'records', WORKFLOW: 'exchange' }[type] || 'circle')
const detail = (span: AgentTraceSpan) => {
  if (!span.detailJson) return ''
  try {
    const data = JSON.parse(span.detailJson) as Record<string, unknown>
    return Object.entries(data).filter(([key]) => !['promptTokens', 'completionTokens', 'totalTokens', 'durationMs'].includes(key)).map(([key, value]) => `${key}: ${String(value)}`).join(' · ')
  } catch { return span.detailJson }
}
onMounted(load)
</script>

<template>
  <main class="trace-page">
    <header><button type="button" aria-label="返回运营助手" @click="router.push('/agent')"><van-icon name="arrow-left" size="24" /></button><div><strong>Agent Trace</strong><span>链路耗时与 Token 使用</span></div><button type="button" aria-label="刷新" @click="load"><van-icon name="replay" size="22" /></button></header>
    <div class="trace-layout">
      <aside aria-label="链路列表"><button v-for="trace in traces" :key="trace.traceId" type="button" :class="{ active: selected?.trace.traceId === trace.traceId }" @click="selectTrace(trace)"><span><b :class="trace.status.toLowerCase()"></b>{{ trace.status }}</span><strong>{{ trace.model || '未知模型' }}</strong><small>{{ formatDate(trace.startTime) }} · {{ formatTime(trace.durationMs) }}</small></button><van-empty v-if="!traces.length && !loading" description="还没有链路记录" /></aside>
      <section v-if="selected" class="detail-panel">
        <div class="summary">
          <article><span>总耗时</span><strong>{{ formatTime(selected.trace.durationMs) }}</strong></article>
          <article><span>输入 Token</span><strong>{{ selected.trace.promptTokens.toLocaleString() }}</strong></article>
          <article><span>输出 Token</span><strong>{{ selected.trace.completionTokens.toLocaleString() }}</strong></article>
          <article><span>总 Token</span><strong>{{ selected.trace.totalTokens.toLocaleString() }}</strong></article>
        </div>
        <section class="token-card"><div><h2>模型调用 Token</h2><p>每次模型请求的输入与输出</p></div><div v-for="(span, index) in llmSpans" :key="span.id" class="token-row"><span>调用 {{ index + 1 }}</span><div class="token-bar"><i :style="{ width: `${span.totalTokens ? span.promptTokens / span.totalTokens * 100 : 0}%` }"></i></div><b>{{ span.promptTokens }} + {{ span.completionTokens }}</b></div><p v-if="!llmSpans.length" class="empty-tip">本次链路没有模型 Token 数据</p></section>
        <section class="timeline"><h2>执行时间线</h2><article v-for="span in selected.spans" :key="span.id" :class="span.status.toLowerCase()"><div class="node"><van-icon :name="typeIcon(span.spanType)" /></div><div class="span-content"><header><div><strong>{{ label(span) }}</strong><span>{{ span.spanType }}</span></div><b>{{ formatTime(span.durationMs) }}</b></header><div v-if="span.durationMs" class="duration"><i :style="{ width: `${Math.max(2, span.durationMs / maxDuration * 100)}%` }"></i></div><p v-if="detail(span)">{{ detail(span) }}</p><small v-if="span.totalTokens">输入 {{ span.promptTokens }} · 输出 {{ span.completionTokens }} · 共 {{ span.totalTokens }} Token</small></div></article></section>
      </section>
      <van-loading v-else-if="loading" class="page-loading" />
    </div>
  </main>
</template>

<style scoped>
.trace-page { min-height: 100vh; color: #16303a; background: #f2f8f9; }
.trace-page > header { display: grid; grid-template-columns: 44px 1fr 44px; align-items: center; gap: 10px; min-height: 66px; padding: max(10px, env(safe-area-inset-top)) 16px 10px; border-bottom: 1px solid var(--share-border); background: white; }
header button { width: 44px; height: 44px; border: 0; border-radius: 12px; background: transparent; cursor: pointer; } header div { display: flex; flex-direction: column; } header span { color: var(--share-muted); font-size: 11px; }
.trace-layout { display: grid; max-width: 1180px; min-height: calc(100vh - 66px); margin: 0 auto; }
aside { padding: 12px; overflow-y: auto; border-right: 1px solid var(--share-border); background: white; } aside > button { display: flex; width: 100%; min-height: 76px; flex-direction: column; gap: 3px; margin-bottom: 7px; padding: 11px 12px; border: 1px solid transparent; border-radius: 12px; color: inherit; background: transparent; text-align: left; cursor: pointer; } aside > button.active { border-color: #9ecbd3; background: #edf9fa; } aside span { display: flex; align-items: center; gap: 6px; font-size: 11px; } aside b { width: 7px; height: 7px; border-radius: 50%; background: #22c55e; } aside b.failed { background: #ef4444; } aside small { color: var(--share-muted); }
.detail-panel { min-width: 0; padding: 16px; }
.summary { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; } .summary article { display: flex; min-height: 94px; flex-direction: column; justify-content: center; padding: 16px; border: 1px solid var(--share-border); border-radius: 16px; background: white; } .summary span { color: var(--share-muted); font-size: 12px; } .summary strong { margin-top: 4px; font-size: 24px; font-variant-numeric: tabular-nums; }
.token-card, .timeline { margin-top: 14px; padding: 18px; border: 1px solid var(--share-border); border-radius: 16px; background: white; } h2 { margin: 0; font-size: 17px; } .token-card > div:first-child p { margin: 2px 0 14px; color: var(--share-muted); font-size: 12px; } .token-row { display: grid; grid-template-columns: 58px 1fr auto; align-items: center; gap: 10px; min-height: 34px; font-size: 12px; } .token-bar { height: 9px; overflow: hidden; border-radius: 5px; background: #bbf7d0; } .token-bar i { display: block; height: 100%; background: #0891b2; } .empty-tip { color: var(--share-muted); font-size: 13px; }
.timeline > h2 { margin-bottom: 18px; } .timeline article { position: relative; display: grid; grid-template-columns: 34px 1fr; gap: 12px; padding-bottom: 18px; } .timeline article:not(:last-child)::before { position: absolute; top: 30px; bottom: 0; left: 15px; width: 2px; background: #d8e8eb; content: ''; } .node { z-index: 1; display: grid; place-items: center; width: 32px; height: 32px; border-radius: 10px; color: white; background: var(--share-primary); } article.failed .node { background: #dc2626; } .span-content { min-width: 0; padding-top: 3px; } .span-content header { display: flex; align-items: start; justify-content: space-between; gap: 12px; } .span-content header div { display: flex; flex-direction: column; } .span-content header span { color: var(--share-muted); font-size: 10px; } .span-content header b { font-size: 12px; font-variant-numeric: tabular-nums; } .duration { height: 5px; margin-top: 8px; overflow: hidden; border-radius: 3px; background: #edf4f5; } .duration i { display: block; height: 100%; border-radius: inherit; background: #22c55e; } .span-content p { margin: 7px 0 0; overflow: hidden; color: var(--share-muted); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; } .span-content small { color: var(--share-primary-dark); }
.page-loading { margin: 80px auto; }
@media (min-width: 760px) { .trace-layout { grid-template-columns: 260px 1fr; } .summary { grid-template-columns: repeat(4, minmax(0, 1fr)); } .detail-panel { padding: 22px; } }
@media (max-width: 759px) { aside { display: flex; gap: 8px; overflow-x: auto; border-right: 0; border-bottom: 1px solid var(--share-border); } aside > button { min-width: 210px; } }
</style>
