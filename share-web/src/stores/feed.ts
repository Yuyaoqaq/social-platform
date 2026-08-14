import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getFeed } from '@/api/content'
import type { FeedItem } from '@/types/content'

export const useFeedStore = defineStore('feed', () => {
  const items = ref<FeedItem[]>([])
  const cursor = ref<number | null>(null)
  const activeTag = ref('')
  const loading = ref(false)
  const refreshing = ref(false)
  const finished = ref(false)

  async function loadMore() {
    if (loading.value || refreshing.value || finished.value) return
    loading.value = true
    try {
      const result = await getFeed({
        cursor: cursor.value ?? undefined,
        size: 10,
        tags: activeTag.value ? [activeTag.value] : undefined,
      })
      if (result.code !== 200) throw new Error(result.msg || '加载失败')
      const nextItems = result.data?.list ?? []
      items.value.push(...nextItems)
      cursor.value = result.data?.nextCursor ?? null
      finished.value = nextItems.length === 0 || cursor.value === null
    } finally {
      loading.value = false
    }
  }

  async function refresh(tag = activeTag.value) {
    if (refreshing.value) return
    refreshing.value = true
    activeTag.value = tag
    try {
      const result = await getFeed({ size: 10, tags: tag ? [tag] : undefined })
      if (result.code !== 200) throw new Error(result.msg || '刷新失败')
      items.value = result.data?.list ?? []
      cursor.value = result.data?.nextCursor ?? null
      finished.value = items.value.length === 0 || cursor.value === null
    } finally {
      refreshing.value = false
    }
  }

  return { items, activeTag, loading, refreshing, finished, loadMore, refresh }
})
