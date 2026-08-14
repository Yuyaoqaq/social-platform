<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
const route = useRoute()
const router = useRouter()
const title = typeof route.meta.title === 'string' ? route.meta.title : '页面迁移中'
</script>

<template>
  <main class="coming page-shell">
    <button type="button" aria-label="返回" @click="router.back()"><van-icon name="arrow-left" size="24" /></button>
    <van-empty image="network" :description="`${title}将在下一阶段完成`" />
  </main>
</template>

<style scoped>
.coming { max-width: 620px; margin: 0 auto; }
.coming > button { display: grid; place-items: center; width: 44px; height: 44px; border: 0; border-radius: 12px; background: white; cursor: pointer; }
</style>
