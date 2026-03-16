package com.messagedispatch.config;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import com.messagedispatch.handler.WebSocketMessageHandler;
import com.messagedispatch.model.WebSocketMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Heartbeat Scheduler
 * 
 * Manages periodic heartbeat messages to maintain WebSocket connections
 * and detect stale connections.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HeartbeatScheduler implements ApplicationListener<ApplicationReadyEvent> {

    private final WebSocketMessageHandler messageHandler;
    private final com.messagedispatch.service.SessionManager sessionManager;

    @Value("${websocket.heartbeat-interval:30000}")
    private long heartbeatInterval;

    private ScheduledExecutorService scheduler;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        startHeartbeat();
    }

    /**
     * Start the heartbeat scheduler
     */
    private void startHeartbeat() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "heartbeat-scheduler");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(
                this::sendHeartbeats,
                heartbeatInterval,
                heartbeatInterval,
                TimeUnit.MILLISECONDS
        );

        log.info("Heartbeat scheduler started with interval: {}ms", heartbeatInterval);
    }

    /**
     * Send heartbeat messages to all connected sessions
     */
    private void sendHeartbeats() {
        try {
            int activeConnections = sessionManager.getConnectionCount();
            
            if (activeConnections == 0) {
                return;
            }

            log.debug("Sending heartbeats to {} connections", activeConnections);
            
            // Broadcast heartbeat to all connections
            WebSocketMessage heartbeat = WebSocketMessage.heartbeat();
            sessionManager.broadcast(heartbeat);

        } catch (Exception e) {
            log.error("Error sending heartbeats: {}", e.getMessage());
        }
    }

    /**
     * Stop the heartbeat scheduler
     */
    public void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("Heartbeat scheduler stopped");
        }
    }
}
