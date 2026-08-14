<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showFailToast } from 'vant'
import AuthLayout from '@/components/auth/AuthLayout.vue'
import { login } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notifications'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const notifications = useNotificationStore()
const loading = ref(false)
const form = reactive({ name: '', pwd: '' })

const submit = async () => {
  loading.value = true
  try {
    const result = await login(form)
    if (result.code !== 200) return showFailToast(result.msg || '登录失败')
    authStore.setSession(result.data)
    notifications.connect()
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
      ? route.query.redirect : '/home/index'
    await router.replace(redirect)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthLayout title="欢迎回来" description="登录后继续浏览和创作你的内容。">
    <van-form required="auto" validate-first @submit="submit">
      <van-cell-group inset>
        <van-field v-model.trim="form.name" name="name" label="用户名" autocomplete="username" placeholder="2～8 位用户名"
          :rules="[{ required: true, message: '请输入用户名' }, { pattern: /^.{2,8}$/, message: '用户名应为 2～8 位' }]" />
        <van-field v-model="form.pwd" name="pwd" label="密码" type="password" autocomplete="current-password" placeholder="6～16 位密码"
          :rules="[{ required: true, message: '请输入密码' }, { pattern: /^[0-9a-zA-Z_]{6,16}$/, message: '密码应为 6～16 位字母、数字或下划线' }]" />
      </van-cell-group>
      <van-button class="primary-button" block round type="primary" native-type="submit" :loading="loading" loading-text="登录中…">登录</van-button>
      <p class="switch-text">还没有账号？<RouterLink to="/register">立即注册</RouterLink></p>
    </van-form>
  </AuthLayout>
</template>

<style scoped>
.primary-button { margin-top: 24px; min-height: 48px; }
.switch-text { margin: 18px 0 0; color: var(--share-muted); text-align: center; font-size: 14px; }
.switch-text a { color: var(--share-primary-dark); font-weight: 700; text-decoration: none; }
</style>
