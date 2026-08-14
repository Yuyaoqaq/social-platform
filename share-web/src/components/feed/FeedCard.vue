<script setup lang="ts">
import type { FeedItem } from '@/types/content'

defineProps<{ item: FeedItem }>()
</script>

<template>
  <RouterLink class="feed-card" :to="`/detail/index/${item.id}`">
    <div class="cover">
      <img v-if="item.picurls?.[0]" :src="item.picurls[0]" :alt="`${item.title}的配图`" loading="lazy" />
      <div v-else class="cover-empty"><van-icon name="photo-o" size="30" aria-hidden="true" /></div>
    </div>
    <div class="content">
      <h2>{{ item.title }}</h2>
      <div v-if="item.tags?.length" class="tags" aria-label="内容标签">
        <span v-for="tag in item.tags.slice(0, 2)" :key="tag">{{ tag }}</span>
      </div>
      <footer>
        <img v-if="item.authorAvatar" :src="item.authorAvatar" :alt="`${item.author}的头像`" loading="lazy" />
        <span class="avatar-fallback" v-else aria-hidden="true">{{ item.author?.slice(0, 1) }}</span>
        <span class="author">{{ item.author }}</span>
        <span class="likes"><van-icon :name="item.isLiked ? 'like' : 'like-o'" aria-hidden="true" /> {{ item.love || 0 }}</span>
      </footer>
    </div>
  </RouterLink>
</template>

<style scoped>
.feed-card { display: block; overflow: hidden; margin-bottom: 14px; border: 1px solid var(--share-border); border-radius: 16px; color: inherit; background: white; text-decoration: none; cursor: pointer; transition: border-color 180ms ease, box-shadow 180ms ease; }
.feed-card:active { border-color: #9bcad4; }
.cover { aspect-ratio: 4 / 5; overflow: hidden; background: #e9f3f5; }
.cover img { width: 100%; height: 100%; object-fit: cover; transition: opacity 180ms ease; }
.cover-empty { display: grid; place-items: center; width: 100%; height: 100%; color: #7b9da5; }
.content { padding: 11px 12px 12px; }
h2 { display: -webkit-box; overflow: hidden; margin: 0; font-size: 15px; line-height: 1.45; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.tags { display: flex; gap: 5px; margin-top: 8px; overflow: hidden; }
.tags span { padding: 2px 7px; border-radius: 6px; color: var(--share-primary-dark); background: #eaf8fa; font-size: 11px; white-space: nowrap; }
footer { display: flex; align-items: center; gap: 7px; margin-top: 11px; color: var(--share-muted); font-size: 12px; }
footer img, .avatar-fallback { width: 25px; height: 25px; flex: 0 0 auto; border-radius: 50%; object-fit: cover; }
.avatar-fallback { display: grid; place-items: center; color: white; background: var(--share-primary); }
.author { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.likes { display: flex; align-items: center; gap: 3px; margin-left: auto; white-space: nowrap; }
@media (hover: hover) { .feed-card:hover { border-color: #9bcad4; box-shadow: 0 8px 24px rgb(15 78 99 / 8%); } }
</style>
