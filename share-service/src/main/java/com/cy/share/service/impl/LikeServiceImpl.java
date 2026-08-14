package com.cy.share.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cy.share.common.constant.RedisConstant;
import com.cy.share.mapper.LikeRecordMapper;
import com.cy.share.mapper.LogMapper;
import com.cy.share.mapper.LogPicMapper;
import com.cy.share.mapper.UserMapper;
import com.cy.share.pojo.LikeRecord;
import com.cy.share.pojo.Log;
import com.cy.share.pojo.LogPic;
import com.cy.share.pojo.User;
import com.cy.share.service.EsSyncProducer;
import com.cy.share.service.LikeService;
import com.cy.share.vo.LikeTop3Vo;
import com.cy.share.vo.LogListVo;
import com.cy.share.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.cy.share.common.constant.RedisConstant.LIKE_SENTINEL;

@Slf4j
@Service
@RequiredArgsConstructor
public class LikeServiceImpl implements LikeService {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    private final LikeRecordMapper likeRecordMapper;
    private final LogMapper logMapper;
    private final LogPicMapper logPicMapper;
    private final UserMapper userMapper;
    private final EsSyncProducer esSyncProducer;
    private final NotificationService notificationService;

    // ---------- 写操作：先 MySQL，再同步 Redis ----------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void like(Integer userId, Integer logId) {
        LikeRecord record = new LikeRecord();
        record.setUserId(userId);
        record.setLogId(logId);
        record.setCreateTime(new Date());

        int inserted = likeRecordMapper.insertIgnore(record);
        if (inserted > 0) {
            logMapper.incrementLove(logId);
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    syncLikeToRedis(userId, logId);
                } catch (Exception e) {
                    log.error("Redis sync failed for like, userId={}, logId={}", userId, logId, e);
                }

                try {
                    esSyncProducer.sendBlogLike(logId);
                } catch (Exception e) {
                    log.error("MQ send failed for like, logId={}", logId, e);
                }

                if (inserted > 0) {
                    try {
                        notificationService.onLike(userId, logId);
                    } catch (Exception e) {
                        log.error("Notification failed for like, userId={}, logId={}", userId, logId, e);
                    }
                }
            }
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlike(Integer userId, Integer logId) {
        int deleted = likeRecordMapper.deleteByUserAndLog(userId, logId);
        if (deleted > 0) {
            logMapper.decrementLove(logId);
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    syncUnlikeToRedis(userId, logId);
                } catch (Exception e) {
                    log.error("Redis sync failed for unlike, userId={}, logId={}", userId, logId, e);
                }

                try {
                    esSyncProducer.sendBlogUnlike(logId);
                } catch (Exception e) {
                    log.error("MQ send failed for unlike, logId={}", logId, e);
                }
            }
        });
    }

    // ---------- 读操作：先 Redis，未命中回退 MySQL ----------

    @Override
    public boolean isLiked(Integer userId, Integer logId) {
        String key = RedisConstant.LIKE_LOG_PREFIX + logId;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score != null) {
            return true;
        }

        // 哨兵存在 → 博客确认无赞 → 该用户必然未赞
        if (stringRedisTemplate.opsForZSet().score(key, LIKE_SENTINEL) != null) {
            return false;
        }

        // ZSet 有数据（不含该用户） → 博客有赞但该用户不在其中
        Long card = stringRedisTemplate.opsForZSet().size(key);
        if (card != null && card > 0) {
            return false;
        }

        // key 不存在，降级 MySQL
        return likeRecordMapper.countByUserIdAndLogId(userId, logId) > 0;
    }

    @Override
    public Map<Integer, Boolean> isLikedBatch(Integer userId, List<Integer> logIds) {
        if (logIds == null || logIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<Integer> likedSet = getLikedLogIds(userId);

        Map<Integer, Boolean> result = new HashMap<>();
        for (Integer logId : logIds) {
            result.put(logId, likedSet.contains(logId));
        }
        return result;
    }

    @Override
    public List<LikeTop3Vo> getTop3Likers(Integer logId) {
        String key = RedisConstant.LIKE_LOG_PREFIX + logId;
        Set<String> top3Strs = stringRedisTemplate.opsForZSet().reverseRange(key, 0, 2);

        if (top3Strs == null || top3Strs.isEmpty()) {
            rebuildLogZSet(logId);
            top3Strs = stringRedisTemplate.opsForZSet().reverseRange(key, 0, 2);
            if (top3Strs == null || top3Strs.isEmpty()) {
                return Collections.emptyList();
            }
        }

        List<Integer> userIds = top3Strs.stream()
                .filter(s -> !LIKE_SENTINEL.equals(s))
                .map(Integer::valueOf)
                .collect(Collectors.toList());
        if (userIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<User> users = userMapper.selectBatchIds(userIds);
        Map<Integer, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<LikeTop3Vo> result = new ArrayList<>();
        for (Integer uid : userIds) {
            User u = userMap.get(uid);
            if (u != null) {
                result.add(new LikeTop3Vo(u.getId(), u.getName(), u.getAvatorurl()));
            }
        }
        return result;
    }

    @Override
    public List<LogListVo> getUserLikedLogs(Integer userId, int page, int size) {
        int start = (page - 1) * size;
        int end = start + size - 1;
        String userKey = RedisConstant.LIKE_USER_PREFIX + userId;

        Set<String> logIdStrs = stringRedisTemplate.opsForZSet()
                .reverseRange(userKey, start, end);

        if (logIdStrs == null || logIdStrs.isEmpty()) {
            rebuildUserZSet(userId);
            logIdStrs = stringRedisTemplate.opsForZSet()
                    .reverseRange(userKey, start, end);
            if (logIdStrs == null || logIdStrs.isEmpty()) {
                return Collections.emptyList();
            }
        }

        List<Integer> logIds = logIdStrs.stream()
                .filter(s -> !LIKE_SENTINEL.equals(s))
                .map(Integer::valueOf)
                .collect(Collectors.toList());
        if (logIds.isEmpty()) {
            return Collections.emptyList();
        }
        return buildLogListVos(logIds);
    }

    // ---------- Redis 同步 ----------

    private void syncLikeToRedis(Integer userId, Integer logId) {
        long ts = System.currentTimeMillis();
        String logKey = RedisConstant.LIKE_LOG_PREFIX + logId;
        String userKey = RedisConstant.LIKE_USER_PREFIX + userId;

        stringRedisTemplate.opsForZSet().add(logKey, userId.toString(), ts);
        stringRedisTemplate.opsForZSet().remove(logKey, LIKE_SENTINEL);
        setTtl(logKey);

        stringRedisTemplate.opsForZSet().add(userKey, logId.toString(), ts);
        stringRedisTemplate.opsForZSet().remove(userKey, LIKE_SENTINEL);
        setTtl(userKey);
    }

    private void syncUnlikeToRedis(Integer userId, Integer logId) {
        stringRedisTemplate.opsForZSet()
                .remove(RedisConstant.LIKE_LOG_PREFIX + logId, userId.toString());
        stringRedisTemplate.opsForZSet()
                .remove(RedisConstant.LIKE_USER_PREFIX + userId, logId.toString());
    }

    // ---------- 缓存重建 ----------

    private void rebuildUserZSet(Integer userId) {
        String key = RedisConstant.LIKE_USER_PREFIX + userId;
        String lockKey = RedisConstant.LIKE_LOCK_USER_PREFIX + userId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                try {
                    Long size = stringRedisTemplate.opsForZSet().size(key);
                    if (size != null && size > 0) {
                        return;
                    }

                    List<LikeRecord> records = likeRecordMapper.selectByUserId(userId);
                    if (records.isEmpty()) {
                        // 确认无赞 → 设哨兵，后续读不穿透 MySQL
                        stringRedisTemplate.opsForZSet().add(key, LIKE_SENTINEL, 0);
                    } else {
                        for (LikeRecord r : records) {
                            stringRedisTemplate.opsForZSet()
                                    .add(key, r.getLogId().toString(),
                                            r.getCreateTime().getTime());
                        }
                    }
                    setTtl(key);
                } finally {
                    lock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void rebuildLogZSet(Integer logId) {
        String key = RedisConstant.LIKE_LOG_PREFIX + logId;
        String lockKey = RedisConstant.LIKE_LOCK_LOG_PREFIX + logId;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                try {
                    // double check
                    Long size = stringRedisTemplate.opsForZSet().size(key);
                    if (size != null && size > 0) {
                        return;
                    }

                    List<LikeRecord> records = likeRecordMapper.selectByLogId(logId);
                    if (records.isEmpty()) {
                        // 确认无赞 → 设哨兵，后续读不穿透 MySQL
                        stringRedisTemplate.opsForZSet().add(key, LIKE_SENTINEL, 0);
                    } else {
                        for (LikeRecord r : records) {
                            stringRedisTemplate.opsForZSet()
                                    .add(key, r.getUserId().toString(),
                                            r.getCreateTime().getTime());
                        }
                    }
                    setTtl(key);
                } finally {
                    lock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---------- 辅助方法 ----------
    private Set<Integer> getLikedLogIds(Integer userId) {
        String userKey = RedisConstant.LIKE_USER_PREFIX + userId;
        Set<String> strs = stringRedisTemplate.opsForZSet().range(userKey, 0, -1);

        if (strs == null || strs.isEmpty()) {
            rebuildUserZSet(userId);
            strs = stringRedisTemplate.opsForZSet().range(userKey, 0, -1);
            if (strs == null || strs.isEmpty()) {
                return Collections.emptySet();
            }
        }

        return strs.stream()
                .filter(s -> !LIKE_SENTINEL.equals(s))
                .map(Integer::valueOf)
                .collect(Collectors.toSet());
    }

    private List<LogListVo> buildLogListVos(List<Integer> logIds) {
        if (logIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Log> logs = logMapper.selectBatchIds(logIds);
        Map<Integer, Log> logMap = logs.stream()
                .collect(Collectors.toMap(Log::getId, l -> l));

        List<LogPic> pics = logPicMapper.selectByLogIds(logIds);
        Map<Integer, List<String>> picMap = pics.stream()
                .collect(Collectors.groupingBy(LogPic::getLogId,
                        Collectors.mapping(LogPic::getPicurl, Collectors.toList())));

        Set<String> authorNames = logs.stream()
                .map(Log::getAuthor)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, String> avatarMap = authorNames.isEmpty() ? Collections.emptyMap()
                : userMapper.selectList(new QueryWrapper<User>().in("name", authorNames))
                        .stream()
                        .collect(Collectors.toMap(User::getName, User::getAvatorurl, (a, b) -> a));

        List<LogListVo> result = new ArrayList<>();
        for (Integer logId : logIds) {
            Log l = logMap.get(logId);
            if (l != null) {
                LogListVo vo = new LogListVo();
                vo.setId(l.getId());
                vo.setTitle(l.getTitle());
                vo.setAuthor(l.getAuthor());
                vo.setLove(l.getLove());
                vo.setIsLiked(true);
                vo.setPicurls(picMap.getOrDefault(logId, new ArrayList<>()));
                vo.setAuthorAvatar(avatarMap.get(l.getAuthor()));
                result.add(vo);
            }
        }
        return result;
    }

    private void setTtl(String key) {
        int ttl = RedisConstant.LIKE_TTL_BASE_SECONDS
                + ThreadLocalRandom.current().nextInt(RedisConstant.LIKE_TTL_RANDOM_SECONDS);
        stringRedisTemplate.expire(key, ttl, TimeUnit.SECONDS);
    }
}
