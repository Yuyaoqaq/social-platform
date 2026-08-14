import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { TOKEN_STORAGE_KEY } from '@/api/http'

export interface LikeNotification {
  type: string
  blogId: number
  blogTitle: string
  count: number
  message: string
  timestamp: number
  read?: boolean
}

export const useNotificationStore = defineStore('notifications', () => {
  const items = ref<LikeNotification[]>([])
  const connected = ref(false)
  let socket: WebSocket | null = null
  let retryTimer: number | undefined
  const unreadCount = computed(() => items.value.filter((item) => !item.read).length)

  function wsBaseUrl() {
    const configured = import.meta.env.VITE_WS_BASE_URL
    if (configured) return configured
    return `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}`
  }

  function connect() {
    const token = localStorage.getItem(TOKEN_STORAGE_KEY)
    if (!token || socket?.readyState === WebSocket.OPEN || socket?.readyState === WebSocket.CONNECTING) return
    socket = new WebSocket(`${wsBaseUrl()}/ws/notifications?token=${encodeURIComponent(token)}`)
    socket.onopen = () => { connected.value = true }
    socket.onmessage = (event) => {
      try { items.value.unshift({ ...(JSON.parse(event.data) as LikeNotification), read: false }) } catch { /* 忽略非法消息 */ }
    }
    socket.onclose = () => {
      connected.value = false
      retryTimer = window.setTimeout(connect, 5_000)
    }
  }

  function disconnect() { if (retryTimer) window.clearTimeout(retryTimer); socket?.close(); socket = null }
  function markAllRead() { items.value.forEach((item) => { item.read = true }) }
  return { items, connected, unreadCount, connect, disconnect, markAllRead }
})
