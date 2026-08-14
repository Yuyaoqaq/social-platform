import axios from 'axios'
import { http } from '@/api/http'
import type { ApiResult, UserSession } from '@/types/api'
import type { LoginPayload, OssPolicy, PhoneRegisterPayload, ProfileRegisterPayload } from '@/types/auth'

export const login = (payload: LoginPayload) =>
  http.post<never, ApiResult<UserSession>>('/login', payload)

export const sendRegisterCode = (phone: string) =>
  http.post<never, ApiResult<string>>('/register/send-code', { phone })

export const verifyRegisterPhone = (payload: PhoneRegisterPayload) =>
  http.post<never, ApiResult<null>>('/register', payload)

export const completeRegister = (payload: ProfileRegisterPayload) =>
  http.post<never, ApiResult<UserSession>>('/registerover', payload)

export const getOssPolicy = () => http.get<never, ApiResult<OssPolicy>>('/oss/policy')

export async function uploadToOss(file: File, policy: OssPolicy): Promise<string> {
  const suffix = file.name.includes('.') ? file.name.slice(file.name.lastIndexOf('.')) : '.jpg'
  const key = `${policy.dir}${crypto.randomUUID()}${suffix}`
  const form = new FormData()
  form.append('OSSAccessKeyId', policy.accessid)
  form.append('policy', policy.policy)
  form.append('signature', policy.signature)
  form.append('key', key)
  form.append('success_action_status', '200')
  form.append('file', file)
  await axios.post(policy.host, form, { timeout: 20_000 })
  return `${policy.host}/${key}`
}
