<script setup lang="ts">
import { ref } from 'vue'
import { showFailToast, showSuccessToast, type UploaderAfterRead } from 'vant'
import { getOssPolicy, uploadToOss } from '@/api/auth'

const model = defineModel<string>({ required: true })
const uploading = ref(false)

const beforeRead = (file: File | File[]) => {
  const target = Array.isArray(file) ? file[0] : file
  if (!['image/jpeg', 'image/png'].includes(target.type)) {
    showFailToast('请选择 JPG 或 PNG 图片')
    return false
  }
  if (target.size > 20 * 1024 * 1024) {
    showFailToast('图片不能超过 20MB')
    return false
  }
  return true
}

const afterRead: UploaderAfterRead = async (item) => {
  const target = Array.isArray(item) ? item[0] : item
  if (!target.file) return
  target.status = 'uploading'
  target.message = '上传中'
  uploading.value = true
  try {
    const policyResult = await getOssPolicy()
    if (policyResult.code !== 200) throw new Error(policyResult.msg || '获取上传凭证失败')
    model.value = await uploadToOss(target.file, policyResult.data)
    target.status = 'done'
    showSuccessToast('头像上传成功')
  } catch (error) {
    target.status = 'failed'
    target.message = '上传失败'
    showFailToast(error instanceof Error ? error.message : '头像上传失败')
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <div class="avatar-field">
    <span class="label">头像</span>
    <van-uploader :before-read="beforeRead" :after-read="afterRead" :max-count="1" reupload>
      <div class="avatar" :class="{ uploading }">
        <img v-if="model" :src="model" alt="已上传的头像" />
        <van-icon v-else name="photograph" size="28" aria-hidden="true" />
      </div>
    </van-uploader>
    <span class="hint">点击上传 JPG/PNG</span>
  </div>
</template>

<style scoped>
.avatar-field { display: grid; grid-template-columns: 80px 72px 1fr; align-items: center; gap: 12px; padding: 12px 16px; }
.label { color: #344d56; }
.avatar { display: grid; place-items: center; width: 64px; height: 64px; overflow: hidden; border: 1px dashed #9ab7bf; border-radius: 50%; color: var(--share-primary-dark); background: #eefafa; cursor: pointer; }
.avatar.uploading { opacity: .55; }
.avatar img { width: 100%; height: 100%; object-fit: cover; }
.hint { color: var(--share-muted); font-size: 13px; }
</style>
