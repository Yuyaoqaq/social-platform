<script setup lang="ts">
import { ref } from 'vue'
import { showFailToast, showSuccessToast } from 'vant'
import { getOssPolicy, uploadToOss } from '@/api/auth'

const images = defineModel<string[]>({ required: true })
const input = ref<HTMLInputElement>()
const uploading = ref(false)
const maxCount = 9

const openPicker = () => input.value?.click()
const remove = (index: number) => { images.value = images.value.filter((_, itemIndex) => itemIndex !== index) }

const selectFiles = async (event: Event) => {
  const target = event.target as HTMLInputElement
  const files = Array.from(target.files || [])
  target.value = ''
  if (!files.length) return
  if (files.some((file) => !['image/jpeg', 'image/png'].includes(file.type))) return showFailToast('只支持 JPG 和 PNG 图片')
  if (files.some((file) => file.size > 20 * 1024 * 1024)) return showFailToast('单张图片不能超过 20MB')
  const available = maxCount - images.value.length
  if (available <= 0) return showFailToast(`最多上传 ${maxCount} 张图片`)

  uploading.value = true
  try {
    const selected = files.slice(0, available)
    for (const file of selected) {
      const policyResult = await getOssPolicy()
      if (policyResult.code !== 200) throw new Error(policyResult.msg || '获取上传凭证失败')
      const url = await uploadToOss(file, policyResult.data)
      images.value = [...images.value, url]
    }
    showSuccessToast('图片上传成功')
  } catch (error) {
    showFailToast(error instanceof Error ? error.message : '图片上传失败')
  } finally { uploading.value = false }
}
</script>

<template>
  <section class="uploader" aria-labelledby="image-title">
    <div class="section-title"><div><h2 id="image-title">内容图片</h2><p>最多 9 张，支持 JPG/PNG</p></div><span>{{ images.length }}/{{ maxCount }}</span></div>
    <div class="image-grid">
      <div v-for="(image, index) in images" :key="image" class="image-item">
        <img :src="image" :alt="`已上传图片 ${index + 1}`" />
        <button type="button" :aria-label="`删除第 ${index + 1} 张图片`" @click="remove(index)"><van-icon name="cross" /></button>
      </div>
      <button v-if="images.length < maxCount" class="add-image" type="button" :disabled="uploading" @click="openPicker">
        <van-loading v-if="uploading" size="24" />
        <van-icon v-else name="photograph" size="28" />
        <span>{{ uploading ? '上传中' : '添加图片' }}</span>
      </button>
    </div>
    <input ref="input" class="file-input" type="file" accept="image/jpeg,image/png" multiple @change="selectFiles" />
  </section>
</template>

<style scoped>
.uploader { padding: 18px; border: 1px solid var(--share-border); border-radius: 18px; background: white; }
.section-title { display: flex; align-items: start; justify-content: space-between; gap: 16px; margin-bottom: 14px; }
h2 { margin: 0; font-size: 17px; }
p { margin: 2px 0 0; color: var(--share-muted); font-size: 12px; }
.section-title > span { color: var(--share-primary-dark); font-size: 13px; }
.image-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 9px; }
.image-item, .add-image { position: relative; aspect-ratio: 1; overflow: hidden; border-radius: 12px; }
.image-item img { width: 100%; height: 100%; object-fit: cover; }
.image-item button { position: absolute; top: 5px; right: 5px; display: grid; place-items: center; width: 28px; height: 28px; border: 0; border-radius: 50%; color: white; background: rgb(22 48 58 / 75%); cursor: pointer; }
.add-image { display: flex; align-items: center; justify-content: center; flex-direction: column; gap: 6px; border: 1px dashed #91b9c2; color: var(--share-primary-dark); background: #eff9fa; cursor: pointer; }
.add-image span { font-size: 12px; }
.add-image:disabled { cursor: wait; opacity: .65; }
.file-input { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0, 0, 0, 0); }
</style>
