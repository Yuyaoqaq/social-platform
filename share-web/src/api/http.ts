import axios, { AxiosError } from 'axios'
import { showFailToast } from 'vant'
import type { ApiResult } from '@/types/api'

export const TOKEN_STORAGE_KEY = 'token'

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 10_000,
  paramsSerializer: { indexes: null },
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_STORAGE_KEY)
  if (token) config.headers.token = encodeURIComponent(token)
  return config
})

http.interceptors.response.use(
  (response) => response.data,
  (error: AxiosError<ApiResult<unknown>>) => {
    if (!error.response) {
      showFailToast('网络异常，请检查连接')
      return Promise.reject(error)
    }

    const message = error.response.data?.msg || `请求失败（${error.response.status}）`
    if (error.response.status === 401) {
      localStorage.removeItem(TOKEN_STORAGE_KEY)
      showFailToast(message.includes('过期') ? '登录已过期，请重新登录' : '登录状态失效')
      const current = `${location.pathname}${location.search}`
      if (!location.pathname.startsWith('/login') && !location.pathname.startsWith('/register')) {
        location.replace(`/login?redirect=${encodeURIComponent(current)}`)
      }
    } else {
      showFailToast(message)
    }
    return Promise.reject(error)
  },
)
