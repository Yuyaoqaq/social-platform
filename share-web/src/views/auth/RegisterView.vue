<script setup lang="ts">
import { onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showFailToast, showSuccessToast } from 'vant'
import AuthLayout from '@/components/auth/AuthLayout.vue'
import { sendRegisterCode, verifyRegisterPhone } from '@/api/auth'

const router = useRouter()
const form = reactive({ phone: '', code: '' })
const sending = ref(false)
const submitting = ref(false)
const countdown = ref(0)
let timer: number | undefined

const startCountdown = () => {
  countdown.value = 60
  timer = window.setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0 && timer) window.clearInterval(timer)
  }, 1000)
}

const sendCode = async () => {
  if (!/^\d{11}$/.test(form.phone)) return showFailToast('请输入正确手机号')
  sending.value = true
  try {
    const result = await sendRegisterCode(form.phone)
    if (result.code !== 200) return showFailToast(result.msg || '发送失败')
    showSuccessToast(result.data ? `验证码：${result.data}` : '验证码已发送')
    startCountdown()
  } finally {
    sending.value = false
  }
}

const submit = async () => {
  submitting.value = true
  try {
    const result = await verifyRegisterPhone(form)
    if (result.code !== 200) return showFailToast(result.msg || '验证码错误')
    await router.push({ name: 'register-profile', query: { phone: form.phone } })
  } finally {
    submitting.value = false
  }
}

onBeforeUnmount(() => { if (timer) window.clearInterval(timer) })
</script>

<template>
  <AuthLayout step="第 1 步，共 2 步" title="创建账号" description="先验证手机号，再完善你的公开资料。">
    <van-form required="auto" validate-first @submit="submit">
      <van-cell-group inset>
        <van-field v-model.trim="form.phone" name="phone" label="手机号" type="tel" maxlength="11" autocomplete="tel" placeholder="请输入手机号"
          :rules="[{ required: true, message: '请输入手机号' }, { pattern: /^\d{11}$/, message: '手机号格式不正确' }]" />
        <van-field v-model.trim="form.code" name="code" label="验证码" type="digit" maxlength="6" autocomplete="one-time-code" placeholder="6 位验证码"
          :rules="[{ required: true, message: '请输入验证码' }, { pattern: /^\d{6}$/, message: '验证码应为 6 位数字' }]">
          <template #button>
            <van-button size="small" type="primary" native-type="button" :disabled="sending || countdown > 0" :loading="sending" @click="sendCode">
              {{ countdown > 0 ? `${countdown}s` : '发送验证码' }}
            </van-button>
          </template>
        </van-field>
      </van-cell-group>
      <van-button class="primary-button" block round type="primary" native-type="submit" :loading="submitting">下一步</van-button>
      <p class="switch-text">已有账号？<RouterLink to="/login">返回登录</RouterLink></p>
    </van-form>
  </AuthLayout>
</template>

<style scoped>
.primary-button { margin-top: 24px; min-height: 48px; }
.switch-text { margin: 18px 0 0; color: var(--share-muted); text-align: center; font-size: 14px; }
.switch-text a { color: var(--share-primary-dark); font-weight: 700; text-decoration: none; }
</style>
