export interface LoginPayload { name: string; pwd: string }
export interface PhoneRegisterPayload { phone: string; code: string }
export interface ProfileRegisterPayload {
  phone: string
  name: string
  pwd: string
  avatorurl: string
  ip: string
  sign: string
}

export interface OssPolicy {
  accessid: string
  policy: string
  signature: string
  dir: string
  host: string
}
