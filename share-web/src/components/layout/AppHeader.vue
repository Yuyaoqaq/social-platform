<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notifications'

const router = useRouter()
const authStore = useAuthStore()
const notifications = useNotificationStore()
const keyword = ref('')
const showMenu = ref(false)

const search = () => {
  if (!keyword.value.trim()) return
  router.push({ path: '/search', query: { q: keyword.value.trim() } })
}

const logout = async () => {
  showMenu.value = false
  notifications.disconnect()
  authStore.clearSession()
  await router.replace('/login')
}
</script>

<template>
  <header class="app-header">
    <RouterLink to="/home/index" class="brand" aria-label="返回首页">
      <span class="brand-mark" aria-hidden="true">S</span>
      <span>Share</span>
    </RouterLink>
    <form class="search" role="search" @submit.prevent="search">
      <van-icon name="search" aria-hidden="true" />
      <input v-model.trim="keyword" aria-label="搜索内容" placeholder="搜索感兴趣的内容" />
    </form>
    <button class="icon-button" type="button" aria-label="打开 AI 运营助手" @click="router.push('/agent')"><van-icon name="chat-o" size="22" /></button>
    <van-badge :content="notifications.unreadCount || undefined" max="99"><button class="icon-button" type="button" aria-label="查看通知" @click="notifications.markAllRead(); showToast(notifications.items[0]?.message || '暂无新通知')"><van-icon name="bell" size="22" /></button></van-badge>
    <button class="menu-button" type="button" aria-label="打开账号菜单" @click="showMenu = true">
      <van-icon name="ellipsis" size="24" />
    </button>
  </header>
  <van-action-sheet v-model:show="showMenu" title="账号设置" cancel-text="取消">
    <button class="sheet-action danger" type="button" @click="logout">退出登录</button>
  </van-action-sheet>
</template>

<style scoped>
.app-header { position: sticky; top: 0; z-index: 20; display: grid; grid-template-columns: auto minmax(0, 1fr) 44px 44px 44px; align-items: center; gap: 5px; padding: max(10px, env(safe-area-inset-top)) 12px 10px; border-bottom: 1px solid var(--share-border); background: rgb(255 255 255 / 94%); backdrop-filter: blur(12px); }
.brand { display: flex; align-items: center; gap: 8px; color: var(--share-text); font-weight: 800; text-decoration: none; }
.brand-mark { display: grid; place-items: center; width: 32px; height: 32px; border-radius: 10px; color: #fff; background: var(--share-primary); }
.search { display: flex; align-items: center; gap: 8px; min-width: 0; height: 42px; padding: 0 13px; border: 1px solid transparent; border-radius: 14px; color: var(--share-muted); background: #eff7f8; }
.search:focus-within { border-color: var(--share-primary); background: #fff; }
.search input { min-width: 0; width: 100%; border: 0; outline: 0; color: var(--share-text); background: transparent; }
.menu-button, .icon-button { display: grid; place-items: center; width: 44px; height: 44px; padding: 0; border: 0; border-radius: 12px; color: var(--share-text); background: transparent; cursor: pointer; }
.menu-button:active { background: #edf7f8; }
.sheet-action { width: 100%; min-height: 54px; border: 0; background: white; cursor: pointer; }
.danger { color: var(--share-danger); font-weight: 700; }
@media (max-width: 560px) { .brand > span:last-child { display: none; } .app-header { grid-template-columns: 32px minmax(0, 1fr) 40px 40px; } .menu-button { display: none; } .icon-button { width: 40px; } }
</style>
