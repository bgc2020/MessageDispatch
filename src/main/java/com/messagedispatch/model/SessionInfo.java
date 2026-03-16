package com.messagedispatch.model;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.web.socket.WebSocketSession;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WebSocket Session Information
 * 
 * Contains all metadata and state information for a WebSocket connection.
 * Each session is uniquely bound to a user account.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionInfo {

    /**
     * WebSocket session ID
     */
    private String sessionId;

    /**
     * Bound user account identifier
     */
    private String userId;

    /**
     * User authentication token (optional)
     */
    private String authToken;

    /**
     * Connection establishment timestamp
     */
    private Instant connectedAt;

    /**
     * Last activity timestamp
     */
    private Instant lastActivityAt;

    /**
     * Connection status
     */
    private ConnectionStatus status;

    /**
     * Client IP address
     */
    private String clientIp;

    /**
     * User agent information
     */
    private String userAgent;

    /**
     * Client-specific attributes
     */
    @Builder.Default
    private Map<String, Object> attributes = new ConcurrentHashMap<>();

    /**
     * Number of messages received from this client
     */
    @Builder.Default
    private AtomicLong messagesReceived = new AtomicLong(0);

    /**
     * Number of messages sent to this client
     */
    @Builder.Default
    private AtomicLong messagesSent = new AtomicLong(0);

    /**
     * Number of reconnection attempts
     */
    @Builder.Default
    private int reconnectionAttempts = 0;

    /**
     * Reference to the underlying WebSocket session
     */
    private transient WebSocketSession session;

    /**
     * Connection Status Enum
     */
    public enum ConnectionStatus {
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        DISCONNECTED,
        RECONNECTING,
        ERROR
    }

    /**
     * Update last activity timestamp
     */
    public void updateActivity() {
        this.lastActivityAt = Instant.now();
    }

    /**
     * Increment received message count
     */
    public void incrementMessagesReceived() {
        this.messagesReceived.incrementAndGet();
    }

    /**
     * Increment sent message count
     */
    public void incrementMessagesSent() {
        this.messagesSent.incrementAndGet();
    }

    /**
     * Get connection duration in milliseconds
     */
    public long getConnectionDurationMs() {
        if (connectedAt == null) {
            return 0;
        }
        return Instant.now().toEpochMilli() - connectedAt.toEpochMilli();
    }

    /**
     * Check if session is active
     */
    public boolean isActive() {
        return status == ConnectionStatus.CONNECTED && session != null && session.isOpen();
    }

    /**
     * Add custom attribute
     */
    public void addAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    /**
     * Get custom attribute
     */
    public Object getAttribute(String key) {
        return attributes.get(key);
    }
}
