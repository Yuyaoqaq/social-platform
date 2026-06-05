

# 短图文社交平台 — 技术文档

> Spring Boot 3.3 + JDK 17 | MyBatis-Plus | MySQL 8.0 | Redis 7.x | Elasticsearch 8.x | RocketMQ 5.x



## 一、登录注册：无状态鉴权 + 会话可吊销

### 1.1 双Token认证模型

| Token | 存储位置 | 职责 | 特性 |
|-------|---------|------|------|
| accessToken | 客户端内存 | 鉴权凭证 | JWT自包含，本地验签，**不查任何存储** |
| refreshToken | Redis + HttpOnly Cookie | 续期凭证 | UUID随机串，可主动删除吊销 |

**核心思路**：accessToken实现无状态鉴权，refreshToken实现会话可吊销。两者分离，各司其职。

### 1.2 本地验签流程

```
请求 → Interceptor → 取accessToken → RSA256本地验签 → 解析userId → ThreadLocal.set()
                                                                       ↓
                                                              Controller直接取用
                                                                       ↓
                                                             afterCompletion清理
```

**关键设计**：
- **不查Redis、不查DB**，验签全程本地完成，无IO开销
- `ThreadLocal` 存userId，请求结束后 `afterCompletion` 强制清理，**防线程复用串数据**

### 1.3 滑动续期（Rotation）

```
accessToken过期(4001) → 客户端自动带refreshToken请求续期
                              ↓
               服务端校验Redis中refreshToken有效性
                              ↓
               发放新accessToken + 新refreshToken（旧refreshToken删除）
```

- 过期码分类：`4001`=Token过期可续，`4002`=Token非法需重登
- 细粒度错误码，客户端可精确决策行为

### 1.4 安全设计

| 措施 | 解决的问题 |
|------|-----------|
| BCrypt哈希存储密码 | 库泄露不可逆 |
| HttpOnly Cookie传refreshToken | XSS无法窃取 |
| verify()验证后立即delete | 一次性消费防重放 |
| 登出删除Redis中refreshToken | 会话主动吊销 |
| `@UnInterception`白名单注解 | 声明式鉴权，零侵入放行注册/登录接口 |

---

## 二、浏览：三场景分治 + 游标分页

### 2.1 我的笔记：MySQL游标分页

```sql
SELECT * FROM log
WHERE create_time < #{cursorTime}
ORDER BY create_time DESC LIMIT #{size}
```

- **Keyset Pagination**：O(1)定位，避免OFFSET深翻页全表扫描
- 用创建时间作游标字段，天然有序、索引命中

### 2.2 赞过笔记：Redis缓存优先读

```
请求 → Redis ZSet(userId:likes)查logIds
          ↓ 命中
        直接返回
          ↓ 未命中
        查MySQL → 回种Redis（带哨兵值）→ 返回
```

**防穿透**：查DB确认无赞时，ZSet写入哨兵值`-1`，下次相同请求命中哨兵直接返回空，不穿DB。

**防击穿**：缓存过期瞬间，`Redisson.tryLock()` 抢锁，抢到者重建缓存，未抢到者自旋等待，避免DB被并发打爆。

### 2.3 Feed流笔记：ES Search After复合游标

```json
{
  "query": { "match_all": {} },
  "size": 20,
  "sort": [
    { "create_time": "desc" },
    { "id": "desc" }
  ],
  "search_after": [1699999999000, "log_12345"]
}
```

**为什么用复合游标**：同毫秒插入多条笔记时，单一时间游标可能漂移导致数据重复或丢失。时间戳+id双字段排序，保证游标唯一且有序。

**收益**：
- 规避深分页性能衰减，翻第5000页与第1页开销一致
- 避免动态插入导致的记录重复消费

---

## 三、发布：事务原子性 + 异步最终一致

### 3.1 STS直传OSS

```
客户端 → 服务端申请STS临时凭证 → 客户端直传OSS → 回传文件URL
```

- 图片数据**不经过服务端**，带宽内存零消耗
- STS临时凭证有时效，防凭证泄露

### 3.2 发布流程

```
1. 开启本地事务
2. INSERT log 表
3. INSERT log_pics 表（多图元数据）
4. 提交事务
5. TransactionSynchronization.afterCommit() → 发MQ消息
```

**关键设计**：
- 事务保证 `log` 与 `log_pics` 两表原子写入
- **TransactionSynchronization钩子**在事务提交后才发MQ，解耦事务边界与外部I/O，DB连接不被MQ发送拉长

### 3.3 消息可靠性：三层兜底

```
MQ发送成功 → 消费者同步ES
     ↓ 发送失败
  落库本地备份表

消费者失败 → 重试3次 → 失败入死信(DLQ)
                         ↓
                    定时任务订阅DLQ，写回本地事务表
                         ↓
                    凌晨2点扫描补偿
                         ↓
                    失败超10条 → 模拟报警
```

**三层兜底**：
- 第一层：生产端异常落库
- 第二层：消费端重试三次入死信
- 第三层：死信订阅 + 定时补偿 + 告警

**分区有序**：RocketMQ按 `logId` 分区，同一笔记的消息不乱序，ES同步不出现旧数据覆盖新数据。

---

## 四、点赞：双维度缓存 + 幂等写入

### 4.1 双维度缓存模型

| 维度 | Redis结构 | 用途 |
|------|----------|------|
| 用户维度 | `ZSet user:likes:{userId}` | Feed流展示点赞状态 |
| 博客维度 | `ZSet blog:likedBy:{logId}` | 详情页展示点赞用户 |

**查询链路**：

```
请求(isLiked)
    ↓
Redis ZSet查用户维度缓存
    ↓ 命中 → 直接返回
    ↓ 未命中
查MySQL → Redisson.tryLock()抢锁
              ↓ 抢到 → 查DB重建缓存
              ↓ 未抢到 → 自旋等缓存
```

**批量优化**：Feed流场景，一次取出用户ZSet全部logId，存内存Set，循环用 `set.contains(logId)` 判断，**消灭N+1查询，单次网络往返**。

### 4.2 点赞/取消点赞

```sql
-- 点赞
INSERT IGNORE INTO like_record (user_id, log_id) VALUES (?, ?);

-- 取消点赞
DELETE FROM like_record WHERE user_id = ? AND log_id = ?;
UPDATE log SET love = love - 1 WHERE id = ? AND love > 0;
```

**幂等设计**：
- `INSERT IGNORE` + `(user_id, log_id)` 联合唯一索引，**单语句原子防重**
- `love = love - 1 AND love > 0`，**DB层原子防负**，不依赖应用层判断

### 4.3 写后处理

```
事务提交 → TransactionSynchronization.afterCommit()
                ↓
         Redis ZSet更新（随机TTL 24h±2h）
                ↓
         MQ异步双写ES
```

**防雪崩**：24h基础TTL加上±2h随机偏移，批量过期不集中在同一时刻。

**原子更新**：Redis使用Pipeline或Lua脚本保证ZSet操作原子性。

---

## 五、搜索：权重分词 + 游标翻页

### 5.1 ES搜索策略

```json
{
  "query": {
    "multi_match": {
      "query": "用户输入关键词",
      "fields": ["title^3", "content"],
      "type": "best_fields"
    }
  }
}
```

- `title^3`：标题权重3倍于内容，匹配标题优先展示
- `best_fields`：取最佳匹配字段得分，非简单求和

### 5.2 翻页与兜底

- **游标翻页**：复用 `search_after`，深翻页性能无退化
- **冷启动兜底**：关键词为空时降级 `match_all`，按时间倒序展示，保证新用户体验

### 5.3 索引同步

- 发布笔记通过MQ异步同步ES，不阻塞主流程
- 点赞数更新通过MQ异步刷新ES中的 `love_count` 字段

---

## 六、基础增强（非核心但工程完备）

| 机制 | 实现方式 | 作用 |
|------|---------|------|
| 乐观锁 | MyBatis-Plus `@Version` | 并发更新防护 |
| 逻辑删除 | MP `@TableLogic` | 数据可追溯 |
| 自动填充 | `MetaObjectHandler` | createTime/updateTime统一注入 |
| 权限控制 | `@UnInterception` + Filter | 声明式放行，颗粒度接口鉴权 |

---

> 文档仅展示核心技术设计，完整代码见各模块源码。
