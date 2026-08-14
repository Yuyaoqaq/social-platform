<script setup lang="ts">
import AppHeader from '@/components/layout/AppHeader.vue'
import BottomNav from '@/components/layout/BottomNav.vue'
import FeedView from '@/views/home/FeedView.vue'
import { useFeedStore } from '@/stores/feed'

const feed = useFeedStore()
const channels = [
  { label: '推荐', value: '' },
  { label: '穿搭', value: '穿搭' },
  { label: '美食', value: '美食' },
  { label: '旅行', value: '旅行' },
  { label: '学习', value: '学习' },
  { label: '数码', value: '数码' },
]

const changeChannel = async (tag: string) => {
  if (tag === feed.activeTag) return
  try { await feed.refresh(tag) } catch { /* 请求层会显示具体错误 */ }
}
</script>

<template>
  <div class="home-page">
    <AppHeader />
    <nav class="channels" aria-label="内容分类">
      <button v-for="channel in channels" :key="channel.label" type="button" :class="{ active: feed.activeTag === channel.value }" @click="changeChannel(channel.value)">
        {{ channel.label }}
      </button>
    </nav>
    <main><FeedView /></main>
    <BottomNav />
  </div>
</template>

<style scoped>
.home-page { min-height: 100vh; max-width: 760px; margin: 0 auto; background: var(--share-background); }
.channels { position: sticky; top: 63px; z-index: 15; display: flex; gap: 6px; overflow-x: auto; padding: 10px 14px; border-bottom: 1px solid var(--share-border); background: rgb(245 251 252 / 96%); scrollbar-width: none; }
.channels::-webkit-scrollbar { display: none; }
.channels button { min-width: 58px; min-height: 40px; padding: 0 15px; border: 0; border-radius: 12px; color: var(--share-muted); background: transparent; white-space: nowrap; cursor: pointer; transition: color 180ms ease, background 180ms ease; }
.channels button.active { color: var(--share-primary-dark); background: #dcf4f7; font-weight: 800; }
</style>
