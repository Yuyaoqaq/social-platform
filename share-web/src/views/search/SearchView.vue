<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showFailToast } from 'vant'
import FeedCard from '@/components/feed/FeedCard.vue'
import { searchContents } from '@/api/content'
import type { FeedItem } from '@/types/content'

const route = useRoute()
const router = useRouter()
const keyword = ref(typeof route.query.q === 'string' ? route.query.q : '')
const searchedKeyword = ref('')
const items = ref<FeedItem[]>([])
const cursor = ref<string | undefined>()
const loading = ref(false)
const finished = ref(true)
const leftColumn = computed(() => items.value.filter((_, index) => index % 2 === 0))
const rightColumn = computed(() => items.value.filter((_, index) => index % 2 === 1))

const load = async (reset = false) => {
  const query = keyword.value.trim()
  if (!query || loading.value || (!reset && finished.value)) return
  loading.value = true
  if (reset) { items.value = []; cursor.value = undefined; finished.value = false; searchedKeyword.value = query }
  try {
    const result = await searchContents(query, cursor.value)
    if (result.code !== 200) return showFailToast(result.msg || '搜索失败')
    const list = result.data?.list ?? []
    items.value.push(...list)
    cursor.value = result.data?.nextCursor ?? undefined
    finished.value = !list.length || !cursor.value
  } finally { loading.value = false }
}

const submit = async () => { await router.replace({ query: { q: keyword.value.trim() } }); await load(true) }
watch(() => route.query.q, (value) => { if (typeof value === 'string' && value !== searchedKeyword.value) { keyword.value = value; load(true) } }, { immediate: true })
</script>

<template>
  <main class="search-page">
    <header>
      <button type="button" aria-label="返回" @click="router.back()"><van-icon name="arrow-left" size="24" /></button>
      <form role="search" @submit.prevent="submit"><van-icon name="search" /><input v-model.trim="keyword" autofocus aria-label="搜索内容" placeholder="搜索标题、内容或标签" /><button type="submit">搜索</button></form>
    </header>
    <p v-if="searchedKeyword" class="result-title">“{{ searchedKeyword }}”的搜索结果</p>
    <van-list :loading="loading" :finished="finished" finished-text="没有更多结果" @load="load(false)">
      <van-empty v-if="searchedKeyword && !items.length && !loading" image="search" description="没有找到相关内容" />
      <div v-else class="results"><div><FeedCard v-for="item in leftColumn" :key="item.id" :item="item" /></div><div><FeedCard v-for="item in rightColumn" :key="item.id" :item="item" /></div></div>
    </van-list>
  </main>
</template>

<style scoped>
.search-page { min-height: 100vh; max-width: 760px; margin: 0 auto; }
header { position: sticky; top: 0; z-index: 20; display: grid; grid-template-columns: 44px 1fr; gap: 8px; padding: max(10px, env(safe-area-inset-top)) 14px 10px; border-bottom: 1px solid var(--share-border); background: rgb(255 255 255 / 95%); }
header > button { width: 44px; height: 44px; border: 0; border-radius: 12px; background: transparent; cursor: pointer; }
form { display: grid; grid-template-columns: 24px 1fr auto; align-items: center; gap: 6px; height: 44px; padding-left: 12px; border: 1px solid var(--share-border); border-radius: 14px; background: #eff7f8; }
input { min-width: 0; border: 0; outline: 0; background: transparent; }
form button { align-self: stretch; padding: 0 14px; border: 0; border-radius: 0 14px 14px 0; color: white; background: var(--share-primary); cursor: pointer; }
.result-title { margin: 18px 16px 0; color: var(--share-muted); font-size: 14px; }
.results { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; padding: 14px; }
</style>
