<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { closeToast, showFailToast, showImagePreview, showLoadingToast, showSuccessToast } from 'vant'
import { getContentDetail, likeContent, unlikeContent } from '@/api/content'
import type { ContentDetail } from '@/types/content'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const detail = ref<ContentDetail | null>(null)
const actionLoading = ref(false)
const id = computed(() => Number(route.params.id))
const timeInfo = computed(() => {
  if (!detail.value) return { label: '', value: '' }
  return detail.value.updateTime && detail.value.updateTime !== detail.value.createTime
    ? { label: '更新于', value: detail.value.updateTime }
    : { label: '发布于', value: detail.value.createTime }
})

const load = async () => {
  if (!Number.isInteger(id.value) || id.value <= 0) return showFailToast('内容编号无效')
  showLoadingToast({ message: '加载中…', forbidClick: true, duration: 0 })
  try {
    const result = await getContentDetail(id.value)
    if (result.code !== 200) {
      closeToast()
      return showFailToast(result.msg || '内容加载失败')
    }
    detail.value = result.data
  } finally { closeToast() }
}

const toggleLike = async () => {
  if (!detail.value || actionLoading.value) return
  actionLoading.value = true
  const wasLiked = Boolean(detail.value.isLiked)
  try {
    const result = wasLiked ? await unlikeContent(id.value) : await likeContent(id.value)
    if (result.code !== 200) return showFailToast(result.msg || '操作失败')
    detail.value.isLiked = !wasLiked
    detail.value.love = Math.max(0, detail.value.love + (wasLiked ? -1 : 1))
    showSuccessToast(wasLiked ? '已取消喜欢' : '已喜欢')
  } finally { actionLoading.value = false }
}

onMounted(load)
</script>

<template>
  <main class="detail-page">
    <header class="detail-header">
      <button type="button" aria-label="返回上一页" @click="router.back()"><van-icon name="arrow-left" size="24" /></button>
      <img v-if="detail?.avatarurl" :src="detail.avatarurl" :alt="`${detail.author}的头像`" />
      <div class="author"><strong>{{ detail?.author || '内容详情' }}</strong><span v-if="detail">{{ timeInfo.label }} {{ timeInfo.value }}</span></div>
      <button v-if="detail" class="like-button" :class="{ liked: detail.isLiked }" type="button" :disabled="actionLoading" :aria-label="detail.isLiked ? '取消喜欢' : '喜欢这篇内容'" @click="toggleLike">
        <van-icon :name="detail.isLiked ? 'like' : 'like-o'" size="21" /><span>{{ detail.love || 0 }}</span>
      </button>
    </header>

    <template v-if="detail">
      <section v-if="detail.picurls?.length" class="gallery" aria-label="内容图片">
        <button v-for="(image, index) in detail.picurls" :key="image" type="button" :aria-label="`查看第 ${index + 1} 张大图`" @click="showImagePreview({ images: detail.picurls, startPosition: index })">
          <img :src="image" :alt="`${detail.title}配图 ${index + 1}`" />
        </button>
      </section>
      <article>
        <h1>{{ detail.title }}</h1>
        <p>{{ detail.info }}</p>
        <button v-if="detail.author === auth.user?.name" class="edit-button" type="button" @click="router.push({ path: '/release', query: { id } })"><van-icon name="edit" /> 编辑这篇内容</button>
      </article>
    </template>
    <van-skeleton v-else title avatar :row="6" class="detail-skeleton" />
  </main>
</template>

<style scoped>
.detail-page { min-height: 100vh; max-width: 760px; margin: 0 auto; padding-bottom: max(30px, env(safe-area-inset-bottom)); background: white; }
.detail-header { position: sticky; top: 0; z-index: 20; display: grid; grid-template-columns: 44px 42px minmax(0, 1fr) auto; align-items: center; gap: 10px; padding: max(10px, env(safe-area-inset-top)) 14px 10px; border-bottom: 1px solid var(--share-border); background: rgb(255 255 255 / 94%); backdrop-filter: blur(12px); }
.detail-header button { display: grid; place-items: center; min-width: 44px; min-height: 44px; padding: 0; border: 0; border-radius: 12px; background: transparent; cursor: pointer; }
.detail-header > img { width: 42px; height: 42px; border-radius: 50%; object-fit: cover; }
.author { display: flex; min-width: 0; flex-direction: column; }
.author strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.author span { color: var(--share-muted); font-size: 11px; }
.like-button { display: flex !important; grid-auto-flow: column; gap: 5px; padding: 0 8px !important; color: var(--share-muted); }
.like-button.liked { color: #d9465f; }
.gallery { display: grid; gap: 3px; background: #edf4f5; }
.gallery button { min-height: 180px; padding: 0; border: 0; background: #e5eff1; cursor: zoom-in; }
.gallery img { display: block; width: 100%; max-height: 76vh; object-fit: cover; }
article { padding: 24px 20px; }
h1 { margin: 0 0 14px; color: var(--share-text); font-size: clamp(24px, 7vw, 34px); line-height: 1.3; letter-spacing: -.025em; }
article p { margin: 0; color: #314c55; line-height: 1.8; white-space: pre-wrap; }
.edit-button { display: flex; align-items: center; gap: 6px; min-height: 44px; margin-top: 26px; padding: 0 14px; border: 1px solid var(--share-border); border-radius: 12px; color: var(--share-primary-dark); background: white; font-weight: 700; cursor: pointer; }
.detail-skeleton { padding: 30px 20px; }
@media (min-width: 760px) { .gallery { margin: 18px; overflow: hidden; border-radius: 20px; } article { padding-inline: 28px; } }
</style>
