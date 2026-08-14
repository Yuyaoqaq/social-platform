<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { showFailToast } from 'vant'
import FeedCard from '@/components/feed/FeedCard.vue'
import { useFeedStore } from '@/stores/feed'

const feed = useFeedStore()
const leftColumn = computed(() => feed.items.filter((_, index) => index % 2 === 0))
const rightColumn = computed(() => feed.items.filter((_, index) => index % 2 === 1))

const load = async () => {
  try { await feed.loadMore() } catch (error) { showFailToast(error instanceof Error ? error.message : '加载失败') }
}
const refresh = async () => {
  try { await feed.refresh() } catch (error) { showFailToast(error instanceof Error ? error.message : '刷新失败') }
}

onMounted(() => { if (!feed.items.length) load() })
</script>

<template>
  <van-pull-refresh :model-value="feed.refreshing" @refresh="refresh">
    <van-list :loading="feed.loading" :finished="feed.finished" finished-text="已经到底了" @load="load">
      <div v-if="!feed.items.length && feed.loading" class="waterfall skeletons" aria-label="内容加载中">
        <div v-for="column in 2" :key="column" class="column">
          <div v-for="item in 3" :key="item" class="skeleton"><van-skeleton-image image-size="100%" /><van-skeleton title :row="2" /></div>
        </div>
      </div>
      <van-empty v-else-if="!feed.items.length && feed.finished" image="search" description="暂时没有内容" />
      <div v-else class="waterfall">
        <div class="column"><FeedCard v-for="item in leftColumn" :key="item.id" v-memo="[item.id, item.love, item.isLiked]" :item="item" /></div>
        <div class="column"><FeedCard v-for="item in rightColumn" :key="item.id" v-memo="[item.id, item.love, item.isLiked]" :item="item" /></div>
      </div>
    </van-list>
  </van-pull-refresh>
</template>

<style scoped>
.waterfall { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; padding: 14px 14px 104px; }
.column { min-width: 0; }
.skeleton { overflow: hidden; margin-bottom: 14px; padding-bottom: 10px; border: 1px solid var(--share-border); border-radius: 16px; background: white; }
.skeleton :deep(.van-skeleton-image) { width: 100% !important; height: auto !important; aspect-ratio: 4 / 5; border-radius: 0; }
.skeleton :deep(.van-skeleton) { padding-top: 12px; }
@media (min-width: 760px) { .waterfall { gap: 18px; padding-inline: 20px; } }
</style>
