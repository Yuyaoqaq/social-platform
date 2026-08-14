<script setup lang="ts">
import { onMounted } from 'vue'
import { TOKEN_STORAGE_KEY } from '@/api/http'
import { useNotificationStore } from '@/stores/notifications'

const notifications = useNotificationStore()
onMounted(() => { if (localStorage.getItem(TOKEN_STORAGE_KEY)) notifications.connect() })
</script>

<template>
  <RouterView />
</template>
