<script setup lang="ts">
import FeedCard from '@/components/feed/FeedCard.vue'
import type { FeedItem } from '@/types/content'

defineProps<{ items: FeedItem[]; loading: boolean; finished: boolean; emptyText: string }>()
defineEmits<{ load: [] }>()
</script>

<template>
  <van-list :loading="loading" :finished="finished" finished-text="没有更多了" @load="$emit('load')">
    <van-empty v-if="!items.length && !loading" image="search" :description="emptyText" />
    <div v-else class="profile-grid">
      <FeedCard v-for="item in items" :key="item.id" :item="item" />
    </div>
  </van-list>
</template>

<style scoped>
.profile-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; padding: 14px 14px 108px; }
</style>
