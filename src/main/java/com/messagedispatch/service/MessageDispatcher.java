package com.messagedispatch.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messagedispatch.metrics.MetricsCollector;
import com.messagedispatch.model.DispatchResult;
import com.messagedispatch.model.WebSocketMessage;

import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Message Dispatcher Service
 * 
 * Core service responsible for:
 * - Routing messages to appropriate destinations
 * - Broadcasting to all connected users
 * - Unicasting to specific users
 * - Message acknowledgment tracking
 * - Retry mechanism for failed deliveries
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageDispatcher {

    private final SessionManager sessionManager;
    private final RedisMessagePublisher redisPublisher;
    private final ObjectMapper objectMapper;
    private final MetricsCollector metricsCollector;

    @Value("${message.dispatch.thread-pool-size:100}")
    private int threadPoolSize;

    @Value("${message.dispatch.retry-attempts:3}")
    private int retryAttempts;

    @Value("${message.dispatch.retry-delay:1000}")
    private long retryDelay;

    private final Map<String, PendingMessage> pendingAcks = new ConcurrentHashMap<>();
    
    private ExecutorService executorService;

    /**
     * Dispatch a message based on its type
     */
    public DispatchResult dispatch(WebSocketMessage message) {
        long startTime = System.currentTimeMillis();
        
        try {
            if (message.getTimestamp() == null) {
                message.setTimestamp(Instant.now());
            }
            if (message.getMessageId() == null) {
                message.setMessageId(java.util.UUID.randomUUID().toString());
            }

            int recipientCount;
            int deliveredCount;

            switch (message.getType()) {
                case BROADCAST:
                    recipientCount = sessionManager.getConnectionCount();
                    deliveredCount = handleBroadcast(message);
                    break;
                    
                case UNICAST:
                    List<String> targetUsers = message.getTargetUsers();
                    recipientCount = targetUsers != null ? targetUsers.size() : 0;
                    deliveredCount = handleUnicast(message);
                    break;
                    
                case SYSTEM:
                    recipientCount = sessionManager.getConnectionCount();
                    deliveredCount = handleSystemMessage(message);
                    break;
                    
                default:
                    return DispatchResult.failure(message.getMessageId(), 
                            "Unsupported message type: " + message.getType());
            }

            long processingTime = System.currentTimeMillis() - startTime;
            
            // Track pending acknowledgment if required
            if (Boolean.TRUE.equals(message.getRequireAck())) {
                trackPendingAck(message, recipientCount);
            }

            metricsCollector.recordDispatchLatency(processingTime);
            
            return DispatchResult.success(message.getMessageId(), recipientCount, deliveredCount, processingTime);

        } catch (Exception e) {
            log.error("Failed to dispatch message: {}", e.getMessage(), e);
            metricsCollector.incrementMessageFailed();
            return DispatchResult.failure(message.getMessageId(), e.getMessage());
        }
    }

    /**
     * Handle broadcast message - send to all connected users
     */
    private int handleBroadcast(WebSocketMessage message) {
        // Publish to Redis for distributed delivery
        redisPublisher.publishBroadcast(message);
        
        // Also deliver locally
        int localCount = sessionManager.broadcast(message);
        
        log.info("Broadcast message dispatched: messageId={}, localDelivered={}", 
                message.getMessageId(), localCount);
        
        return localCount;
    }

    /**
     * Handle unicast message - send to specific users
     */
    private int handleUnicast(WebSocketMessage message) {
        List<String> targetUsers = message.getTargetUsers();
        
        if (targetUsers == null || targetUsers.isEmpty()) {
            log.warn("Unicast message has no target users: messageId={}", message.getMessageId());
            return 0;
        }

        // Publish to Redis for distributed delivery
        redisPublisher.publishUnicast(message);
        
        // Also deliver locally to connected users
        int deliveredCount = sessionManager.sendToUsers(targetUsers, message);
        
        log.info("Unicast message dispatched: messageId={}, targetCount={}, delivered={}", 
                message.getMessageId(), targetUsers.size(), deliveredCount);
        
        return deliveredCount;
    }

    /**
     * Handle system message - similar to broadcast but for system notifications
     */
    private int handleSystemMessage(WebSocketMessage message) {
        redisPublisher.publishSystem(message);
        return sessionManager.broadcast(message);
    }

    /**
     * Track pending acknowledgment
     */
    private void trackPendingAck(WebSocketMessage message, int expectedAcks) {
        PendingMessage pending = new PendingMessage(message, expectedAcks, Instant.now());
        pendingAcks.put(message.getMessageId(), pending);
        
        // Schedule timeout check
        scheduleAckTimeoutCheck(message.getMessageId());
    }

    /**
     * Process acknowledgment from client
     */
    public void processAcknowledgment(String userId, String messageId) {
        PendingMessage pending = pendingAcks.get(messageId);
        
        if (pending != null) {
            pending.incrementReceivedAcks();
            
            if (pending.allAcksReceived()) {
                pendingAcks.remove(messageId);
                log.debug("All acknowledgments received for message: {}", messageId);
            }
        }
    }

    /**
     * Schedule timeout check for pending acknowledgment
     */
    private void scheduleAckTimeoutCheck(String messageId) {
        // In production, this should use a scheduled executor or delay queue
        // For simplicity, we'll just log the timeout scenario
        log.debug("Acknowledgment tracking started for message: {}", messageId);
    }

    /**
     * Get pending acknowledgment count
     */
    public int getPendingAckCount() {
        return pendingAcks.size();
    }

    /**
     * Retry failed message delivery
     */
    public void retryDispatch(WebSocketMessage message, int attempt) {
        if (attempt >= retryAttempts) {
            log.error("Max retry attempts reached for message: {}", message.getMessageId());
            metricsCollector.incrementMessageRetryFailed();
            return;
        }

        log.info("Retrying message dispatch: messageId={}, attempt={}", message.getMessageId(), attempt);
        
        try {
            Thread.sleep(retryDelay * attempt); // Exponential backoff
            dispatch(message);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Retry failed: {}", e.getMessage());
            retryDispatch(message, attempt + 1);
        }
    }

    /**
     * Initialize executor service
     */
    private ExecutorService getExecutorService() {
        if (executorService == null || executorService.isShutdown()) {
            executorService = Executors.newFixedThreadPool(threadPoolSize);
        }
        return executorService;
    }

    /**
     * Shutdown executor service
     */
    public void shutdown() {
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Pending Message for acknowledgment tracking
     */
    private static class PendingMessage {
        private final WebSocketMessage message;
        private final int expectedAcks;
        private final Instant sentAt;
        private int receivedAcks;

        public PendingMessage(WebSocketMessage message, int expectedAcks, Instant sentAt) {
            this.message = message;
            this.expectedAcks = expectedAcks;
            this.sentAt = sentAt;
            this.receivedAcks = 0;
        }

        public synchronized void incrementReceivedAcks() {
            receivedAcks++;
        }

        public boolean allAcksReceived() {
            return receivedAcks >= expectedAcks;
        }
    }
}
