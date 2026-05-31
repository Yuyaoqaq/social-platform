# 双 Token 认证改造设计

## Context

当前项目使用单一 HMAC-SHA 对称密钥签名的 JWT，30分钟过期，无刷新机制。需要改造为：
- RSA-2048 非对称签名的 accessToken（1小时，请求头传输）
- UUID refreshToken（7天，HttpOnly Cookie 传输，存 Redis）
- 前端无感刷新（accessToken 过期时自动用 refreshToken 换新）
- ThreadLocal 存当前用户 userId，供下游使用

---

## 一、密钥管理

- 启动时用 `KeyPairGenerator` 生成 RSA-2048 密钥对，保存在内存单例 `RsaKeyHolder`
- 每次重启重新生成（refreshToken 存 Redis，不依赖密钥持久化，不影响已登录用户的刷新能力）
- 私钥：签发 accessToken
- 公钥：验签 accessToken

---

## 二、Token 设计

### accessToken
- 算法：RS256（RSA + SHA-256）
- 载荷：`{ userId: "123" }`（只存 userId，最小化载荷）
- 过期：1小时
- 传输：请求头 `Authorization: Bearer <token>`

### refreshToken
- 内容：UUID（无语义，不可伪造）
- Redis key：`auth:refresh:{refreshToken}`，value：userId（字符串）
- TTL：7天，每次刷新时滑动续期（重置为7天）
- 传输：HttpOnly Cookie，`Path=/auth/refresh`，`Max-Age=604800`
- 同一用户重新登录：生成新 UUID，覆盖 Redis 中旧值（旧 refreshToken 自动失效）

---

## 三、新增/改造的类

| 类 | 位置 | 职责 |
|----|------|------|
| `RsaKeyHolder` | `common/utils/` | 启动时生成并持有 RSA 密钥对 |
| `JwtUtil` | `common/utils/` | `createAccessToken(userId)` / `parseAccessToken(token)→userId` |
| `UserContext` | `common/utils/` | ThreadLocal 存取 userId，`get/set/remove` |
| `Jwtconstant` | `common/constant/` | 删 `JWT_SECRET`/`JWT_TTL`，加 `ACCESS_TOKEN_TTL`/`REFRESH_TOKEN_TTL` |
| `RedisConstant` | `common/constant/` | 新增 `REFRESH_TOKEN_PREFIX = "auth:refresh:"` |
| `MyInterceptor` | `config/interceptor/` | 从 `Authorization` 头取 accessToken，验签后存 UserContext，afterCompletion 清除 |
| `AuthController` | `controller/` | 新增 `POST /auth/refresh` 刷新接口 |
| `LoginController` | `controller/login/` | login/registerover 同时签发双 token |
| `LoginVo` | `vo/` | `token` 字段改名为 `accessToken` |

---

## 四、数据流

### 登录/注册
```
POST /login
  → 验证用户
  → createAccessToken(userId)         → 响应体返回
  → UUID refreshToken                 → 存 Redis(auth:refresh:{uuid} = userId, TTL 7d)
                                      → Set-Cookie: refreshToken=uuid; HttpOnly; Path=/auth/refresh
```

### 正常请求
```
请求头: Authorization: Bearer <accessToken>
  → MyInterceptor.preHandle
  → parseAccessToken → userId
  → UserContext.set(userId)
  → 放行
  → afterCompletion: UserContext.remove()
```

### accessToken 过期（前端无感刷新）
```
响应 4001
  → 前端拦截，自动调 POST /auth/refresh（携带 Cookie）
  → AuthController 从 Cookie 取 refreshToken
  → Redis get(auth:refresh:{refreshToken}) → userId
  → 存在：createAccessToken(userId) 返回新 accessToken
         Redis set(auth:refresh:{refreshToken}, userId, TTL 7d)  ← 滑动续期
  → 不存在：返回 401，前端跳登录页
```

### 强制下线
```
Redis del(auth:refresh:{refreshToken})
  → 下次刷新时 Redis 查不到 → 401 → 前端跳登录页
```

---

## 五、错误处理

| 场景 | 返回 |
|------|------|
| 无 accessToken | 401，msg: 无权限，请先登录 |
| accessToken 签名非法 | 401，msg: token 校验不通过 |
| accessToken 过期 | 4001，msg: token 已过期 |
| refreshToken 不存在/已过期 | 401，msg: 登录已失效，请重新登录 |

---

## 六、改造范围（不涉及的内容）

- 不改造 OSS、Log、User 等业务接口
- 不引入新的数据库表
- `@UnInterception` 注解机制保持不变
