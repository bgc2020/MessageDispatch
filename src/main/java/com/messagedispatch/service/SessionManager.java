package com.messagedispatch.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messagedispatch.metrics.MetricsCollector;
import com.messagedispatch.model.SessionInfo;
import com.messagedispatch.model.WebSocketMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket Session Manager
 * 
 * Manages all WebSocket connections with user binding.
 * Provides O(1) lookup for sessions by userId or sessionId.
 * 
 * Features:
 * - Concurrent-safe session storage
 * - User-to-Session binding (one session per user)
 * - Automatic cleanup of stale sessions
 * - Connection statistics and monitoring
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionManager {

    // Session storage: sessionId -> SessionInfo
    private final Map<String, SessionInfo> sessionStore = new ConcurrentHashMap<>();
    
    // User to session mapping: userId -> sessionId
    private final Map<String, String> userSessionMap = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;
    private final MetricsCollector metricsCollector;

    /**
     * Register a new WebSocket session
     */
    public void registerSession(WebSocketSession session, SessionInfo sessionInfo) {
        String sessionId = session.getId();
        String userId = sessionInfo.getUserId();

        // Set the session reference
        sessionInfo.setSession(session);
        sessionInfo.setStatus(SessionInfo.ConnectionStatus.CONNECTED);
        sessionInfo.setSessionId(sessionId);

        // Store session
        sessionStore.put(sessionId, sessionInfo);
        userSessionMap.put(userId, sessionId);

        log.info("Session registered: sessionId={}, userId={}, totalConnections={}", 
                sessionId, userId, sessionStore.size());

        // Update metrics
        metricsCollector.setActiveConnections(sessionStore.size());
        metricsCollector.incrementTotalConnections();
    }

    /**
     * Remove a WebSocket session
     */
    public SessionInfo removeSession(String sessionId) {
        SessionInfo sessionInfo = sessionStore.remove(sessionId);
        
        if (sessionInfo != null) {
            String userId = sessionInfo.getUserId();
            userSessionMap.remove(userId);
            
            sessionInfo.setStatus(SessionInfo.ConnectionStatus.DISCONNECTED);
            
            log.info("Session removed: sessionId={}, userId={}, totalConnections={}", 
                    sessionId, userId, sessionStore.size());
            
            // Update metrics
            metricsCollector.setActiveConnections(sessionStore.size());
            metricsCollector.incrementDisconnections();
        }

        return sessionInfo;
    }

    /**
     * Get session by sessionId
     */
    public Optional<SessionInfo> getSession(String sessionId) {
        return Optional.ofNullable(sessionStore.get(sessionId));
    }

    /**
     * Get session by userId
     */
    public Optional<SessionInfo> getSessionByUserId(String userId) {
        String sessionId = userSessionMap.get(userId);
        if (sessionId == null) {
            return Optional.empty();
        }
        return getSession(sessionId);
    }

    /**
     * Check if user is connected
     */
    public boolean isUserConnected(String userId) {
        return userSessionMap.containsKey(userId);
    }

    /**
     * Get all active sessions
     */
    public Collection<SessionInfo> getAllSessions() {
        return sessionStore.values();
    }

    /**
     * Get all connected user IDs
     */
    public Set<String> getConnectedUsers() {
        return userSessionMap.keySet();
    }

    /**
     * Get total number of active connections
     */
    public int getConnectionCount() {
        return sessionStore.size();
    }

    /**
     * Send message to a specific user
     */
    public boolean sendToUser(String userId, WebSocketMessage message) {
        Optional<SessionInfo> sessionInfoOpt = getSessionByUserId(userId);
        
        if (sessionInfoOpt.isEmpty()) {
            log.debug("User not connected: {}", userId);
            metricsCollector.incrementMessageFailed();
            return false;
        }

        SessionInfo sessionInfo = sessionInfoOpt.get();
        return sendToSession(sessionInfo, message);
    }

    /**
     * Send message to multiple users
     */
    public int sendToUsers(List<String> userIds, WebSocketMessage message) {
        AtomicInteger successCount = new AtomicInteger(0);
        
        for (String userId : userIds) {
            if (sendToUser(userId, message)) {
                successCount.incrementAndGet();
            }
        }

        return successCount.get();
    }

    /**
     * Broadcast message to all connected users
     */
    public int broadcast(WebSocketMessage message) {
        AtomicInteger successCount = new AtomicInteger(0);
        Collection<SessionInfo> sessions = new ArrayList<>(sessionStore.values());
        
        for (SessionInfo sessionInfo : sessions) {
            if (sendToSession(sessionInfo, message)) {
                successCount.incrementAndGet();
            }
        }

        log.info("Broadcast message sent to {} connections", successCount.get());
        return successCount.get();
    }

    /**
     * Send message to a specific session
     */
    private boolean sendToSession(SessionInfo sessionInfo, WebSocketMessage message) {
        WebSocketSession session = sessionInfo.getSession();
        
        if (session == null || !session.isOpen()) {
            log.debug("Session not available: sessionId={}", sessionInfo.getSessionId());
            return false;
        }

        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            synchronized (session) {
                session.sendMessage(new TextMessage(jsonMessage));
            }
            
            sessionInfo.incrementMessagesSent();
            sessionInfo.updateActivity();
            
            metricsCollector.incrementMessageSent();
            
            return true;
        } catch (Exception e) {
            log.error("Failed to send message to session: sessionId={}, error={}", 
                    sessionInfo.getSessionId(), e.getMessage());
            metricsCollector.incrementMessageFailed();
            return false;
        }
    }

    /**
     * Disconnect a user's session
     */
    public void disconnectUser(String userId, String reason) {
        Optional<SessionInfo> sessionInfoOpt = getSessionByUserId(userId);
        
        sessionInfoOpt.ifPresent(sessionInfo -> {
            WebSocketSession session = sessionInfo.getSession();
            if (session != null && session.isOpen()) {
                try {
                    sessionInfo.setStatus(SessionInfo.ConnectionStatus.DISCONNECTING);
                    session.close(new CloseStatus(1000, reason));
                } catch (Exception e) {
                    log.error("Error closing session for user: {}", userId, e);
                }
            }
            removeSession(sessionInfo.getSessionId());
        });
    }

    /**
     * Disconnect all sessions
     */
    public void disconnectAll(String reason) {
        List<SessionInfo> sessions = new ArrayList<>(sessionStore.values());
        
        for (SessionInfo sessionInfo : sessions) {
            WebSocketSession session = sessionInfo.getSession();
            if (session != null && session.isOpen()) {
                try {
                    session.close(new CloseStatus(1001, reason));
                } catch (Exception e) {
                    log.error("Error closing session: {}", sessionInfo.getSessionId(), e);
                }
            }
        }
        
        sessionStore.clear();
        userSessionMap.clear();
        
        metricsCollector.setActiveConnections(0);
    }

    /**
     * Scheduled cleanup of stale sessions
     * Runs every 5 minutes
     */
    @Scheduled(fixedRate = 300000)
    public void cleanupStaleSessions() {
        Instant now = Instant.now();
        Instant staleThreshold = now.minusSeconds(300); // 5 minutes

        List<String> staleSessions = sessionStore.values().stream()
                .filter(sessionInfo -> {
                    if (sessionInfo.getLastActivityAt() == null) {
                        return false;
                    }
                    return sessionInfo.getLastActivityAt().isBefore(staleThreshold);
                })
                .map(SessionInfo::getSessionId)
                .collect(Collectors.toList());

        for (String sessionId : staleSessions) {
            log.warn("Removing stale session: {}", sessionId);
            removeSession(sessionId);
        }

        if (!staleSessions.isEmpty()) {
            log.info("Cleaned up {} stale sessions", staleSessions.size());
        }
    }

    /**
     * Get session statistics
     */
    public Map<String, Object> getStatistics() {
        return Map.of(
            "totalConnections", sessionStore.size(),
            "uniqueUsers", userSessionMap.size(),
            "oldestConnection", sessionStore.values().stream()
                .map(SessionInfo::getConnectedAt)
                .min(Instant::compareTo)
                .map(Instant::toString)
                .orElse("N/A"),
            "averageMessagesReceived", sessionStore.values().stream()
                .mapToLong(s -> s.getMessagesReceived().get())
                .average()
                .orElse(0.0),
            "averageMessagesSent", sessionStore.values().stream()
                .mapToLong(s -> s.getMessagesSent().get())
                .average()
                .orElse(0.0)
        );
    }
}
