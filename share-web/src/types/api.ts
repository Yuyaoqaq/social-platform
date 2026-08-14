export interface ApiResult<T> {
  code: number
  msg?: string
  data: T
  total?: number
}

export interface UserSession {
  token: string
  name: string
  avatorurl?: string
  ip?: string
  sign?: string
}
