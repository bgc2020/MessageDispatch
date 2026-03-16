package com.messagedispatch.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import com.messagedispatch.service.SessionManager;

import lombok.RequiredArgsConstructor;

/**
 * WebSocket Health Indicator
 * 
 * Provides health status information for the WebSocket subsystem.
 * Integrates with Spring Boot Actuator for monitoring and Kubernetes probes.
 */
@Component
@RequiredArgsConstructor
public class WebSocketHealthIndicator implements HealthIndicator {

    private final SessionManager sessionManager;
    private static final int MAX_CONNECTIONS_WARNING_THRESHOLD = 45000;
    private static final int MAX_CONNECTIONS_CRITICAL_THRESHOLD = 50000;

    @Override
    public Health health() {
        int activeConnections = sessionManager.getConnectionCount();
        
        Health.Builder builder;
        
        if (activeConnections >= MAX_CONNECTIONS_CRITICAL_THRESHOLD) {
            builder = Health.status("WARNING")
                    .withDetail("message", "Connection count approaching maximum capacity");
        } else if (activeConnections >= MAX_CONNECTIONS_WARNING_THRESHOLD) {
            builder = Health.status("WARNING")
                    .withDetail("message", "Connection count elevated");
        } else {
            builder = Health.up();
        }
        
        return builder
                .withDetail("activeConnections", activeConnections)
                .withDetail("maxConnections", MAX_CONNECTIONS_CRITICAL_THRESHOLD)
                .withDetail("utilization", String.format("%.2f%%", 
                        (double) activeConnections / MAX_CONNECTIONS_CRITICAL_THRESHOLD * 100))
                .build();
    }
}
