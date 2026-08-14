<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showFailToast, showSuccessToast } from 'vant'
import AuthLayout from '@/components/auth/AuthLayout.vue'
import AvatarUploader from '@/components/auth/AvatarUploader.vue'
import { completeRegister } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notifications'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const notifications = useNotificationStore()
const showAreaPicker = ref(false)
const loading = ref(false)
const areas = ['杭州', '江苏', '山东', '北京', '重庆']
const phone = computed(() => typeof route.query.phone === 'string' ? route.query.phone : '')
const form = reactive({ name: '', pwd: '', avatorurl: '', ip: '', sign: '' })

if (!/^\d{11}$/.test(phone.value)) router.replace('/register')

const confirmArea = ({ selectedOptions }: { selectedOptions: Array<{ text: string | number }> }) => {
  form.ip = String(selectedOptions[0]?.text || '')
  showAreaPicker.value = false
}

const submit = async () => {
  if (!form.avatorurl) return showFailToast('请先上传头像')
  loading.value = true
  try {
    const result = await completeRegister({ ...form, phone: phone.value })
    if (result.code !== 200) return showFailToast(result.msg || '注册失败')
    authStore.setSession(result.data)
    notifications.connect()
    showSuccessToast('注册成功')
    await router.replace('/home/index')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AuthLayout step="第 2 步，共 2 步" title="完善资料" description="这些信息会用于个人主页展示，之后仍可修改。">
    <van-form required="auto" validate-first @submit="submit">
      <AvatarUploader v-model="form.avatorurl" />
      <van-cell-group inset>
        <van-field v-model.trim="form.name" name="name" label="用户名" maxlength="8" autocomplete="username" placeholder="2～8 位用户名"
          :rules="[{ required: true, message: '请输入用户名' }, { pattern: /^.{2,8}$/, message: '用户名应为 2～8 位' }]" />
        <van-field v-model="form.pwd" name="pwd" label="密码" type="password" autocomplete="new-password" placeholder="6～16 位密码"
          :rules="[{ required: true, message: '请输入密码' }, { pattern: /^[0-9a-zA-Z_]{6,16}$/, message: '密码应为 6～16 位字母、数字或下划线' }]" />
        <van-field v-model="form.ip" name="ip" label="城市" readonly is-link placeholder="请选择城市" :rules="[{ required: true, message: '请选择城市' }]" @click="showAreaPicker = true" />
        <van-field v-model.trim="form.sign" name="sign" label="签名" type="textarea" rows="2" autosize maxlength="20" show-word-limit placeholder="介绍一下自己" :rules="[{ required: true, message: '请输入签名' }]" />
      </van-cell-group>
      <van-button class="primary-button" block round type="primary" native-type="submit" :loading="loading">完成注册</van-button>
    </van-form>
    <van-popup v-model:show="showAreaPicker" position="bottom" round>
      <van-picker :columns="areas.map((text) => ({ text, value: text }))" title="选择城市" @confirm="confirmArea" @cancel="showAreaPicker = false" />
    </van-popup>
  </AuthLayout>
</template>

<style scoped>
.primary-button { margin-top: 24px; min-height: 48px; }
</style>
