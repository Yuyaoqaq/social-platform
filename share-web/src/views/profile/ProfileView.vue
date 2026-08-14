<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { showFailToast } from 'vant'
import BottomNav from '@/components/layout/BottomNav.vue'
import ProfileContentList from '@/components/profile/ProfileContentList.vue'
import { getFeed, getLikedContents } from '@/api/content'
import { useAuthStore } from '@/stores/auth'
import type { FeedItem } from '@/types/content'
import { useNotificationStore } from '@/stores/notifications'

const router = useRouter()
const auth = useAuthStore()
const notifications = useNotificationStore()
const activeTab = ref(0)
const showSettings = ref(false)
const works = ref<FeedItem[]>([])
const liked = ref<FeedItem[]>([])
const workCursor = ref<number | null>(null)
const likedPage = ref(1)
const loading = ref(false)
const worksFinished = ref(false)
const likedFinished = ref(false)

const loadWorks = async () => {
  if (loading.value || worksFinished.value) return
  loading.value = true
  try {
    const result = await getFeed({ cursor: workCursor.value ?? undefined, size: 10, author: auth.user?.name })
    if (result.code !== 200) return showFailToast(result.msg || '作品加载失败')
    const list = result.data?.list ?? []
    works.value.push(...list)
    workCursor.value = result.data?.nextCursor ?? null
    worksFinished.value = !list.length || workCursor.value === null
  } finally { loading.value = false }
}

const loadLiked = async () => {
  if (loading.value || likedFinished.value) return
  loading.value = true
  try {
    const result = await getLikedContents(likedPage.value, 10)
    if (result.code !== 200) return showFailToast(result.msg || '喜欢内容加载失败')
    liked.value.push(...(result.data || []))
    likedFinished.value = (result.data || []).length < 10
    likedPage.value += 1
  } finally { loading.value = false }
}

watch(activeTab, (tab) => { if (tab === 0 && !works.value.length) loadWorks(); if (tab === 1 && !liked.value.length) loadLiked() }, { immediate: true })

const logout = async () => {
  showSettings.value = false
  notifications.disconnect()
  auth.clearSession()
  await router.replace('/login')
}
</script>

<template>
  <main class="profile-page">
    <header class="profile-header">
      <button type="button" aria-label="返回首页" @click="router.push('/home/index')"><van-icon name="arrow-left" size="24" /></button>
      <span>个人主页</span>
      <button type="button" aria-label="账号设置" @click="showSettings = true"><van-icon name="setting-o" size="23" /></button>
    </header>
    <section class="profile-card">
      <img v-if="auth.user?.avatorurl" :src="auth.user.avatorurl" :alt="`${auth.user.name}的头像`" />
      <div v-else class="avatar-fallback">{{ auth.user?.name?.slice(0, 1) || 'S' }}</div>
      <div class="identity"><h1>{{ auth.user?.name || 'Share 用户' }}</h1><p>IP 属地：{{ auth.user?.ip || '未设置' }}</p></div>
      <p class="signature">{{ auth.user?.sign || '还没有填写个人签名' }}</p>
    </section>
    <van-tabs v-model:active="activeTab" sticky offset-top="65px" color="#0891b2" title-active-color="#0e7490">
      <van-tab title="作品">
        <ProfileContentList :items="works" :loading="loading" :finished="worksFinished" empty-text="还没有发布作品" @load="loadWorks" />
      </van-tab>
      <van-tab title="赞过">
        <ProfileContentList :items="liked" :loading="loading" :finished="likedFinished" empty-text="还没有喜欢的内容" @load="loadLiked" />
      </van-tab>
    </van-tabs>
    <BottomNav />
    <van-action-sheet v-model:show="showSettings" title="账号设置" cancel-text="取消">
      <button class="logout-button" type="button" @click="logout">退出登录</button>
    </van-action-sheet>
  </main>
</template>

<style scoped>
.profile-page { min-height: 100vh; max-width: 760px; margin: 0 auto; background: var(--share-background); }
.profile-header { position: sticky; top: 0; z-index: 20; display: grid; grid-template-columns: 44px 1fr 44px; align-items: center; padding: max(10px, env(safe-area-inset-top)) 14px 10px; border-bottom: 1px solid var(--share-border); background: rgb(255 255 255 / 95%); text-align: center; font-weight: 800; }
.profile-header button { display: grid; place-items: center; width: 44px; height: 44px; padding: 0; border: 0; border-radius: 12px; background: transparent; cursor: pointer; }
.profile-card { display: grid; grid-template-columns: 80px 1fr; gap: 14px; padding: 28px 20px 22px; background: white; }
.profile-card > img, .avatar-fallback { width: 78px; height: 78px; border-radius: 50%; object-fit: cover; }
.avatar-fallback { display: grid; place-items: center; color: white; background: var(--share-primary); font-size: 30px; font-weight: 800; }
.identity { align-self: center; min-width: 0; }
h1 { margin: 0; overflow: hidden; font-size: 24px; text-overflow: ellipsis; white-space: nowrap; }
.identity p { margin: 4px 0 0; color: var(--share-muted); font-size: 13px; }
.signature { grid-column: 1 / -1; margin: 2px 0 0; color: #344d56; }
.logout-button { width: 100%; min-height: 54px; border: 0; color: var(--share-danger); background: white; font-weight: 700; cursor: pointer; }
</style>
