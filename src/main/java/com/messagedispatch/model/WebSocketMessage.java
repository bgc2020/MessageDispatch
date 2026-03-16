package com.messagedispatch.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WebSocket Message Definition
 * 
 * Standard JSON message format for WebSocket communication.
 * All messages follow this structure for consistency and extensibility.
 * 
 * JSON Format Example:
 * {
 *   "messageId": "550e8400-e29b-41d4-a716-446655440000",
 *   "type": "BROADCAST",
 *   "action": "notification",
 *   "payload": {"content": "Hello World"},
 *   "metadata": {"priority": "high"},
 *   "timestamp": "2024-01-15T10:30:00.000Z",
 *   "source": "system"
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebSocketMessage {

    /**
     * Unique identifier for the message
     * Used for message tracking, acknowledgment, and deduplication
     */
    @JsonProperty("messageId")
    private String messageId;

    /**
     * Message type: BROADCAST, UNICAST, SYSTEM, ACKNOWLEDGMENT, HEARTBEAT
     */
    @JsonProperty("type")
    private MessageType type;

    /**
     * Action or event name (e.g., "notification", "update", "command")
     */
    @JsonProperty("action")
    private String action;

    /**
     * Main payload data
     */
    @JsonProperty("payload")
    private Object payload;

    /**
     * Additional metadata for routing and processing
     */
    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    /**
     * Message creation timestamp in ISO 8601 format
     */
    @JsonProperty("timestamp")
    private Instant timestamp;

    /**
     * Source of the message (system, user_id, service_name)
     */
    @JsonProperty("source")
    private String source;

    /**
     * Target user(s) for unicast messages
     */
    @JsonProperty("targetUsers")
    private java.util.List<String> targetUsers;

    /**
     * Expiration time for time-sensitive messages
     */
    @JsonProperty("expiresAt")
    private Instant expiresAt;

    /**
     * Priority level: LOW, NORMAL, HIGH, CRITICAL
     */
    @JsonProperty("priority")
    private Priority priority;

    /**
     * Require acknowledgment from recipients
     */
    @JsonProperty("requireAck")
    private Boolean requireAck;

    /**
     * Original message ID for acknowledgment response
     */
    @JsonProperty("ackFor")
    private String ackFor;

    /**
     * Message status for tracking
     */
    @JsonProperty("status")
    private MessageStatus status;

    /**
     * Message Types
     */
    public enum MessageType {
        BROADCAST,          // Broadcast to all connected users
        UNICAST,            // Send to specific user(s)
        SYSTEM,             // System-level message
        ACKNOWLEDGMENT,     // Acknowledgment response
        HEARTBEAT,          // Connection keepalive
        ERROR,              // Error notification
        CONTROL             // Control command
    }

    /**
     * Priority Levels
     */
    public enum Priority {
        LOW,
        NORMAL,
        HIGH,
        CRITICAL
    }

    /**
     * Message Status
     */
    public enum MessageStatus {
        PENDING,
        DELIVERED,
        FAILED,
        EXPIRED,
        ACKNOWLEDGED
    }

    /**
     * Create a new broadcast message
     */
    public static WebSocketMessage broadcast(String action, Object payload) {
        return WebSocketMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .type(MessageType.BROADCAST)
                .action(action)
                .payload(payload)
                .timestamp(Instant.now())
                .priority(Priority.NORMAL)
                .build();
    }

    /**
     * Create a new unicast message for specific users
     */
    public static WebSocketMessage unicast(String action, Object payload, java.util.List<String> targetUsers) {
        return WebSocketMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .type(MessageType.UNICAST)
                .action(action)
                .payload(payload)
                .targetUsers(targetUsers)
                .timestamp(Instant.now())
                .priority(Priority.NORMAL)
                .build();
    }

    /**
     * Create an acknowledgment message
     */
    public static WebSocketMessage acknowledgment(String originalMessageId, boolean success, String message) {
        return WebSocketMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .type(MessageType.ACKNOWLEDGMENT)
                .ackFor(originalMessageId)
                .payload(Map.of("success", success, "message", message))
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create a heartbeat message
     */
    public static WebSocketMessage heartbeat() {
        return WebSocketMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .type(MessageType.HEARTBEAT)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create an error message
     */
    public static WebSocketMessage error(String errorMessage, String errorCode) {
        return WebSocketMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .type(MessageType.ERROR)
                .payload(Map.of("error", errorMessage, "code", errorCode))
                .timestamp(Instant.now())
                .priority(Priority.HIGH)
                .build();
    }
}
