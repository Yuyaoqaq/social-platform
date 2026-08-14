<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const go = (path: string) => router.push(path)
</script>

<template>
  <nav class="bottom-nav" aria-label="主要导航">
    <button type="button" :class="{ active: route.path.startsWith('/home') }" @click="go('/home/index')">
      <van-icon name="home-o" size="23" /><span>首页</span>
    </button>
    <button class="create" type="button" aria-label="发布内容" @click="go('/release')">
      <van-icon name="plus" size="26" />
    </button>
    <button type="button" :class="{ active: route.path.startsWith('/my') }" @click="go('/my/index')">
      <van-icon name="user-o" size="23" /><span>我的</span>
    </button>
  </nav>
</template>

<style scoped>
.bottom-nav { position: fixed; z-index: 30; left: 50%; bottom: max(10px, env(safe-area-inset-bottom)); display: grid; grid-template-columns: 1fr 76px 1fr; align-items: center; width: min(calc(100% - 24px), 520px); min-height: 64px; padding: 6px 16px; border: 1px solid var(--share-border); border-radius: 22px; background: rgb(255 255 255 / 96%); box-shadow: 0 12px 40px rgb(22 48 58 / 16%); transform: translateX(-50%); backdrop-filter: blur(12px); }
button { display: flex; min-height: 48px; align-items: center; justify-content: center; gap: 5px; padding: 0; border: 0; color: #61747b; background: transparent; cursor: pointer; }
button span { font-size: 12px; }
button.active { color: var(--share-primary-dark); font-weight: 700; }
.create { justify-self: center; width: 54px; height: 46px; min-height: 46px; border-radius: 16px; color: white; background: var(--share-primary); }
</style>
