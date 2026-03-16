package com.messagedispatch.controller;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.messagedispatch.metrics.MetricsCollector;
import com.messagedispatch.model.DispatchResult;
import com.messagedispatch.model.WebSocketMessage;
import com.messagedispatch.service.MessageDispatcher;
import com.messagedispatch.service.SessionManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Message Dispatch REST Controller
 * 
 * Provides REST API endpoints for:
 * - Sending broadcast messages
 * - Sending unicast messages to specific users
 * - System status and statistics
 * - Health checks
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MessageDispatchController {

    private final MessageDispatcher messageDispatcher;
    private final SessionManager sessionManager;
    private final MetricsCollector metricsCollector;

    /**
     * Send a broadcast message to all connected users
     * 
     * POST /api/v1/broadcast
     * Body: { "action": "notification", "payload": {...}, "metadata": {...} }
     */
    @PostMapping("/broadcast")
    public ResponseEntity<Map<String, Object>> broadcast(@RequestBody Map<String, Object> request) {
        log.info("Received broadcast request: action={}", request.get("action"));
        
        String action = (String) request.getOrDefault("action", "broadcast");
        Object payload = request.get("payload");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) request.get("metadata");
        
        WebSocketMessage message = WebSocketMessage.broadcast(action, payload);
        if (metadata != null) {
            message.setMetadata(metadata);
        }
        
        DispatchResult result = messageDispatcher.dispatch(message);
        
        return ResponseEntity.ok(Map.of(
            "success", result.isSuccess(),
            "messageId", message.getMessageId(),
            "recipientCount", result.getRecipientCount(),
            "deliveredCount", result.getDeliveredCount(),
            "processingTimeMs", result.getProcessingTimeMs()
        ));
    }

    /**
     * Send a unicast message to specific users
     * 
     * POST /api/v1/unicast
     * Body: { 
     *   "targetUsers": ["user1", "user2"],
     *   "action": "notification",
     *   "payload": {...}
     * }
     */
    @PostMapping("/unicast")
    public ResponseEntity<Map<String, Object>> unicast(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<String> targetUsers = (List<String>) request.get("targetUsers");
        
        if (targetUsers == null || targetUsers.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "targetUsers is required and cannot be empty"
            ));
        }
        
        log.info("Received unicast request: targetUsers={}, action={}", 
                targetUsers, request.get("action"));
        
        String action = (String) request.getOrDefault("action", "unicast");
        Object payload = request.get("payload");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) request.get("metadata");
        
        WebSocketMessage message = WebSocketMessage.unicast(action, payload, targetUsers);
        if (metadata != null) {
            message.setMetadata(metadata);
        }
        
        DispatchResult result = messageDispatcher.dispatch(message);
        
        return ResponseEntity.ok(Map.of(
            "success", result.isSuccess(),
            "messageId", message.getMessageId(),
            "targetUsers", targetUsers,
            "deliveredCount", result.getDeliveredCount(),
            "processingTimeMs", result.getProcessingTimeMs()
        ));
    }

    /**
     * Send a message to a single user
     * 
     * POST /api/v1/message/user/{userId}
     */
    @PostMapping("/message/user")
    public ResponseEntity<Map<String, Object>> sendToUser(
            @RequestParam String userId,
            @RequestBody Map<String, Object> request) {
        
        if (userId == null || userId.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "userId is required"
            ));
        }
        
        log.info("Sending message to user: {}", userId);
        
        String action = (String) request.getOrDefault("action", "message");
        Object payload = request.get("payload");
        
        WebSocketMessage message = WebSocketMessage.unicast(action, payload, List.of(userId));
        DispatchResult result = messageDispatcher.dispatch(message);
        
        return ResponseEntity.ok(Map.of(
            "success", result.isSuccess(),
            "messageId", message.getMessageId(),
            "userId", userId,
            "delivered", result.getDeliveredCount() > 0
        ));
    }

    /**
     * Get system statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        // Connection statistics
        stats.put("connections", Map.of(
            "active", sessionManager.getConnectionCount(),
            "uniqueUsers", sessionManager.getConnectedUsers().size()
        ));
        
        // Session manager statistics
        stats.put("sessions", sessionManager.getStatistics());
        
        // Message statistics
        stats.put("pendingAcks", messageDispatcher.getPendingAckCount());
        
        // System info
        stats.put("system", Map.of(
            "timestamp", Instant.now().toString(),
            "uptime", System.currentTimeMillis()
        ));
        
        return ResponseEntity.ok(stats);
    }

    /**
     * Get list of connected users
     */
    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> getConnectedUsers() {
        return ResponseEntity.ok(Map.of(
            "count", sessionManager.getConnectedUsers().size(),
            "users", sessionManager.getConnectedUsers()
        ));
    }

    /**
     * Check if a specific user is connected
     */
    @GetMapping("/users/{userId}/status")
    public ResponseEntity<Map<String, Object>> getUserStatus(@RequestParam String userId) {
        boolean connected = sessionManager.isUserConnected(userId);
        
        return ResponseEntity.ok(Map.of(
            "userId", userId,
            "connected", connected,
            "timestamp", Instant.now().toString()
        ));
    }

    /**
     * Disconnect a specific user
     */
    @PostMapping("/users/disconnect")
    public ResponseEntity<Map<String, Object>> disconnectUser(@RequestParam String userId) {
        log.info("Disconnecting user: {}", userId);
        
        boolean wasConnected = sessionManager.isUserConnected(userId);
        sessionManager.disconnectUser(userId, "Disconnected by admin");
        
        return ResponseEntity.ok(Map.of(
            "userId", userId,
            "wasConnected", wasConnected,
            "disconnected", true,
            "timestamp", Instant.now().toString()
        ));
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "timestamp", Instant.now().toString(),
            "activeConnections", sessionManager.getConnectionCount()
        ));
    }
}
