import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { TOKEN_STORAGE_KEY } from '@/api/http'
import type { UserSession } from '@/types/api'

const USER_STORAGE_KEY = 'share-user'

function restoreUser(): UserSession | null {
  try {
    const raw = localStorage.getItem(USER_STORAGE_KEY)
    return raw ? JSON.parse(raw) as UserSession : null
  } catch {
    localStorage.removeItem(USER_STORAGE_KEY)
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserSession | null>(restoreUser())
  const token = ref(localStorage.getItem(TOKEN_STORAGE_KEY) || '')
  const isAuthenticated = computed(() => Boolean(token.value))

  function setSession(session: UserSession) {
    user.value = session
    token.value = session.token
    localStorage.setItem(TOKEN_STORAGE_KEY, session.token)
    localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(session))
  }

  function clearSession() {
    user.value = null
    token.value = ''
    localStorage.removeItem(TOKEN_STORAGE_KEY)
    localStorage.removeItem(USER_STORAGE_KEY)
  }

  return { user, token, isAuthenticated, setSession, clearSession }
})
