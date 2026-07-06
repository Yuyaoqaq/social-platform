package com.cy.share.websocket;

import com.cy.share.common.utils.JwtResult;
import com.cy.share.common.utils.JwtUtil;
import com.cy.share.common.utils.RsaKeyHolder;
import com.cy.share.config.websocket.SpringContextConfigurator;
import com.cy.share.dto.LikeNotificationMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@ServerEndpoint(value = "/ws/notifications", configurator = SpringContextConfigurator.class)
public class LikeWebSocketServer {

    private final RsaKeyHolder rsaKeyHolder;
    private final ObjectMapper objectMapper;

    /** userId -> all active sessions for that user */
    private final ConcurrentHashMap<Integer, Set<Session>> onlineMap = new ConcurrentHashMap<>();

    private final ThreadPoolExecutor closeExecutor = new ThreadPoolExecutor(
            4, 4, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                t.setName("ws-close");
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy());

    public LikeWebSocketServer(RsaKeyHolder rsaKeyHolder) {
        this.rsaKeyHolder = rsaKeyHolder;
        this.objectMapper = new ObjectMapper();
    }

    @OnOpen
    public void onOpen(Session session, EndpointConfig config) {
        String token = getQueryParam(session, "token");
        if (token == null || token.isEmpty()) {
            closeQuietly(session);
            log.warn("WebSocket connection missing token, session={}", session.getId());
            return;
        }

        JwtResult result = JwtUtil.validateAccessToken(token, rsaKeyHolder.getPublicKey());
        if (!result.isSuccess()) {
            closeQuietly(session);
            log.warn("WebSocket JWT validation failed, session={}", session.getId());
            return;
        }

        String userIdStr = result.getClaims().getSubject();
        Integer userId = Integer.valueOf(userIdStr);

        onlineMap.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("WebSocket user online: userId={}, session={}", userId, session.getId());
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        removeSession(session);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.error("WebSocket error: session={}", session.getId(), error);
        removeSession(session);
    }

    // ---------- Called by NotificationService ----------

    public boolean isOnline(Integer userId) {
        Set<Session> sessions = onlineMap.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    public void push(Integer userId, LikeNotificationMessage message) {
        Set<Session> sessions = onlineMap.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (IOException e) {
            log.error("JSON serialization failed: {}", message, e);
            return;
        }

        for (Session session : sessions) {
            if (session.isOpen()) {
                try {
                    session.getBasicRemote().sendText(json);
                } catch (IOException e) {
                    log.error("Push failed: session={}", session.getId(), e);
                }
            } else {
                sessions.remove(session);
            }
        }
    }

    // ---------- Internal ----------

    private void removeSession(Session session) {
        onlineMap.forEach((userId, sessions) -> {
            if (sessions.remove(session) && sessions.isEmpty()) {
                onlineMap.remove(userId);
                log.info("WebSocket user offline: userId={}", userId);
            }
        });
    }

    private void closeQuietly(Session session) {
        // Async close to avoid blocking onOpen thread on dead connections
        closeExecutor.execute(() -> {
            try {
                if (session.isOpen()) {
                    session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "Unauthorized"));
                }
            } catch (IOException ignored) {
            }
        });
    }

    private String getQueryParam(Session session, String name) {
        String query = session.getQueryString();
        if (query == null) return null;
        for (String param : query.split("&")) {
            //按照=分割，最多分割成2个部分
            String[] pair = param.split("=", 2);
            if (pair.length == 2 && pair[0].equals(name)) {
                return pair[1];
            }
        }
        return null;
    }
}
