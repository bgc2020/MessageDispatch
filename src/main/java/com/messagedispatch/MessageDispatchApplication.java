package com.messagedispatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import lombok.extern.slf4j.Slf4j;

/**
 * High-Performance Real-Time Message Dispatch System
 * 
 * This application provides a scalable message distribution platform based on 
 * Redis Pub/Sub and WebSocket technologies, supporting both broadcast and 
 * unicast messaging patterns with comprehensive telemetry support.
 * 
 * Key Features:
 * - Support for 50,000+ concurrent WebSocket connections
 * - User-account binding for each WebSocket connection
 * - Broadcast and unicast message routing
 * - Redis-based distributed message distribution
 * - Comprehensive metrics and telemetry via Micrometer/Prometheus
 * - Automatic failover and reconnection mechanisms
 * 
 * @author MessageDispatch Team
 * @version 1.0.0
 */
@Slf4j
@EnableAsync
@EnableScheduling
@SpringBootApplication
public class MessageDispatchApplication implements ApplicationListener<ApplicationReadyEvent> {

    public static void main(String[] args) {
        SpringApplication.run(MessageDispatchApplication.class, args);
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("==========================================================");
        log.info("   Message Dispatch System Started Successfully");
        log.info("   WebSocket Endpoint: ws://localhost:8080/ws");
        log.info("   Actuator Endpoint: http://localhost:8080/actuator");
        log.info("   Prometheus Metrics: http://localhost:8080/actuator/prometheus");
        log.info("==========================================================");
    }
}
