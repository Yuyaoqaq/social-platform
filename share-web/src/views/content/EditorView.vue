<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { closeToast, showConfirmDialog, showFailToast, showLoadingToast, showSuccessToast } from 'vant'
import MultiImageUploader from '@/components/content/MultiImageUploader.vue'
import { getEditableContent, publishContent, updateContent } from '@/api/content'
import { useAuthStore } from '@/stores/auth'
import { useFeedStore } from '@/stores/feed'

const DRAFT_KEY = 'share-content-draft'
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const feed = useFeedStore()
const loading = ref(false)
const initialized = ref(false)
const saved = ref(false)
const editId = computed(() => Number(route.query.id) || null)
const form = reactive({ title: '', info: '', picurls: [] as string[] })
const isDirty = computed(() => Boolean(form.title.trim() || form.info.trim() || form.picurls.length))

const restoreLocalDraft = () => {
  if (editId.value) return
  try {
    const raw = localStorage.getItem(DRAFT_KEY)
    if (!raw) return
    const draft = JSON.parse(raw) as typeof form
    form.title = draft.title || ''
    form.info = draft.info || ''
    form.picurls = Array.isArray(draft.picurls) ? draft.picurls : []
  } catch { localStorage.removeItem(DRAFT_KEY) }
}

const loadEdit = async () => {
  if (!editId.value) return restoreLocalDraft()
  showLoadingToast({ message: '读取内容…', duration: 0, forbidClick: true })
  try {
    const result = await getEditableContent(editId.value)
    if (result.code !== 200) return showFailToast(result.msg || '内容读取失败')
    form.title = result.data.title
    form.info = result.data.info
    form.picurls = result.data.picurls || []
  } finally { closeToast() }
}

const saveDraft = () => {
  localStorage.setItem(DRAFT_KEY, JSON.stringify(form))
  showSuccessToast('草稿已保存在本机')
}

const submit = async () => {
  if (!form.picurls.length) return showFailToast('请至少上传一张图片')
  loading.value = true
  try {
    const draft = { ...form, author: auth.user?.name || '', love: 0 }
    const result = editId.value ? await updateContent(editId.value, draft) : await publishContent(draft)
    if (result.code !== 200) return showFailToast(result.msg || (editId.value ? '更新失败' : '发布失败'))
    saved.value = true
    localStorage.removeItem(DRAFT_KEY)
    try { await feed.refresh() } catch { /* 发布已成功，首页可自行重试刷新 */ }
    showSuccessToast(editId.value ? '更新成功' : '发布成功')
    await router.replace('/home/index')
  } finally { loading.value = false }
}

onMounted(async () => { await loadEdit(); initialized.value = true })
onBeforeRouteLeave(async () => {
  if (!initialized.value || saved.value || !isDirty.value) return true
  try { await showConfirmDialog({ title: '离开编辑页？', message: '未发布的修改会丢失，你也可以先保存草稿。', confirmButtonText: '直接离开' }); return true } catch { return false }
})
</script>

<template>
  <main class="editor-page">
    <header>
      <button type="button" aria-label="返回" @click="router.back()"><van-icon name="arrow-left" size="24" /></button>
      <div><strong>{{ editId ? '编辑内容' : '发布内容' }}</strong><span>{{ editId ? '修改后不会改变原发布时间' : '记录此刻，也分享给同好' }}</span></div>
      <button class="draft-button" type="button" @click="saveDraft">存草稿</button>
    </header>
    <van-form class="editor-form" required="auto" validate-first @submit="submit">
      <MultiImageUploader v-model="form.picurls" />
      <section class="text-editor">
        <van-field v-model.trim="form.title" name="title" label="标题" maxlength="22" show-word-limit placeholder="用一句话概括内容"
          :rules="[{ required: true, message: '请输入标题' }]" />
        <van-field v-model.trim="form.info" name="info" label="正文" type="textarea" rows="8" autosize maxlength="1000" show-word-limit placeholder="分享你的经历、方法或发现…"
          :rules="[{ required: true, message: '请输入正文' }]" />
      </section>
      <div class="actions">
        <van-button block round type="primary" native-type="submit" :loading="loading" loading-text="正在保存…">{{ editId ? '保存修改' : '立即发布' }}</van-button>
      </div>
    </van-form>
  </main>
</template>

<style scoped>
.editor-page { min-height: 100vh; max-width: 760px; margin: 0 auto; padding-bottom: max(24px, env(safe-area-inset-bottom)); }
header { position: sticky; top: 0; z-index: 20; display: grid; grid-template-columns: 44px 1fr auto; align-items: center; gap: 10px; padding: max(10px, env(safe-area-inset-top)) 14px 10px; border-bottom: 1px solid var(--share-border); background: rgb(255 255 255 / 94%); backdrop-filter: blur(12px); }
header > button { min-width: 44px; min-height: 44px; padding: 0; border: 0; border-radius: 12px; background: transparent; cursor: pointer; }
header div { display: flex; min-width: 0; flex-direction: column; }
header span { overflow: hidden; color: var(--share-muted); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.draft-button { padding: 0 10px; color: var(--share-primary-dark); font-weight: 700; }
.editor-form { display: grid; gap: 14px; padding: 16px 14px; }
.text-editor { overflow: hidden; border: 1px solid var(--share-border); border-radius: 18px; background: white; }
.text-editor :deep(.van-field__label) { font-weight: 700; }
.actions { padding: 8px 4px; }
.actions .van-button { min-height: 50px; }
</style>
