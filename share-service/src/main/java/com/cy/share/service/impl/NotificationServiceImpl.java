package com.cy.share.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cy.share.dto.LikeNotificationMessage;
import com.cy.share.mapper.LogMapper;
import com.cy.share.mapper.UserMapper;
import com.cy.share.pojo.Log;
import com.cy.share.pojo.User;
import com.cy.share.service.NotificationService;
import com.cy.share.websocket.LikeWebSocketServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final LogMapper logMapper;
    private final UserMapper userMapper;
    private final LikeWebSocketServer webSocketServer;

    /** key = "authorId:blogId" */
    private final ConcurrentHashMap<String, LikeWindow> windows = new ConcurrentHashMap<>();
    private final ThreadPoolTaskScheduler taskScheduler;

    private static final long COOLDOWN_SECONDS = 10;

    @Override
    public void onLike(Integer likerId, Integer logId) {
        // 1. Find blog to get author name
        Log log = logMapper.selectById(logId);
        if (log == null) return;
        String authorName = log.getAuthor();

        // 2. Find author user ID by name
        User author = userMapper.selectOne(
                new QueryWrapper<User>().eq("name", authorName));
        if (author == null) return;
        Integer authorId = author.getId();

        // 3. Don't notify self-likes
        if (authorId.equals(likerId)) return;

        // 4. Author offline, do nothing
        if (!webSocketServer.isOnline(authorId)) return;

        // 5. Get liker's name
        User liker = userMapper.selectById(likerId);
        String likerName = liker != null ? liker.getName() : "未知用户";

        // 6. Rate limiting + push
        String key = authorId + ":" + logId;
        windows.compute(key, (k, existingWindow) -> {
            if (existingWindow == null) {
                // New window: push immediately with count=1
                String message = likerName + "赞了你的图文";
                LikeNotificationMessage msg = LikeNotificationMessage.builder()
                        .type("like")
                        .blogId(logId)
                        .blogTitle(log.getTitle())
                        .count(1)
                        .message(message)
                        .timestamp(System.currentTimeMillis())
                        .build();
                webSocketServer.push(authorId, msg);

                LikeWindow window = new LikeWindow();
                window.firstLikerName = likerName;
                window.pendingCount = 1;
                window.future = taskScheduler.schedule(() -> {
                    LikeWindow w = windows.remove(k);
                    if (w != null && w.pendingCount > 1) {
                        String aggMsg = w.firstLikerName + "等"
                                + w.pendingCount + "人赞了你的图文";
                        LikeNotificationMessage agg = LikeNotificationMessage.builder()
                                .type("like_aggregated")
                                .blogId(logId)
                                .blogTitle(log.getTitle())
                                .count(w.pendingCount)
                                .message(aggMsg)
                                .timestamp(System.currentTimeMillis())
                                .build();
                        webSocketServer.push(authorId, agg);
                    }
                }, Instant.now().plusSeconds(COOLDOWN_SECONDS));
                return window;
            } else {
                // Within cooldown: only increment, no push
                existingWindow.pendingCount++;
                return existingWindow;
            }
        });
    }

    private static class LikeWindow {
        int pendingCount;
        String firstLikerName;
        ScheduledFuture<?> future;
    }
}
