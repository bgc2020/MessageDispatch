package com.messagedispatch.metrics;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;

/**
 * Metrics Collector using Micrometer
 * 
 * Collects comprehensive metrics for the message dispatch system.
 * Exposes metrics via Prometheus endpoint for monitoring and alerting.
 */
@Component
public class MetricsCollector {

    private final MeterRegistry meterRegistry;

    // Counters
    private final Counter totalConnections;
    private final Counter disconnections;
    private final Counter messagesSent;
    private final Counter messagesReceived;
    private final Counter messagesFailed;
    private final Counter heartbeatReceived;
    private final Counter transportErrors;
    private final Counter messageRetryFailed;

    // Gauges
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicInteger pendingAcknowledgments = new AtomicInteger(0);

    // Timers
    private final Timer messageProcessingTimer;
    private final Timer connectionEstablishmentTimer;
    private final Timer handshakeTimer;
    private final Timer dispatchLatencyTimer;

    // Distribution Summary
    private final DistributionSummary connectionDurationSummary;
    private final DistributionSummary messageSizeSummary;

    // Handshake metrics
    private final Counter handshakeSuccess;
    private final Counter handshakeFailure;

    @Getter
    private final AtomicLong lastMessageTimestamp = new AtomicLong(0);

    public MetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Initialize counters
        this.totalConnections = Counter.builder("websocket.connections.total")
                .description("Total number of WebSocket connections established")
                .tag("type", "websocket")
                .register(meterRegistry);

        this.disconnections = Counter.builder("websocket.disconnections.total")
                .description("Total number of WebSocket disconnections")
                .tag("type", "websocket")
                .register(meterRegistry);

        this.messagesSent = Counter.builder("websocket.messages.sent")
                .description("Total number of messages sent")
                .tag("direction", "outbound")
                .register(meterRegistry);

        this.messagesReceived = Counter.builder("websocket.messages.received")
                .description("Total number of messages received")
                .tag("direction", "inbound")
                .register(meterRegistry);

        this.messagesFailed = Counter.builder("websocket.messages.failed")
                .description("Total number of failed message deliveries")
                .tag("type", "error")
                .register(meterRegistry);

        this.heartbeatReceived = Counter.builder("websocket.heartbeat.received")
                .description("Total number of heartbeat messages received")
                .tag("type", "heartbeat")
                .register(meterRegistry);

        this.transportErrors = Counter.builder("websocket.transport.errors")
                .description("Total number of transport errors")
                .tag("type", "error")
                .register(meterRegistry);

        this.messageRetryFailed = Counter.builder("websocket.message.retry.failed")
                .description("Total number of message retry failures")
                .tag("type", "error")
                .register(meterRegistry);

        this.handshakeSuccess = Counter.builder("websocket.handshake.success")
                .description("Total number of successful handshakes")
                .tag("result", "success")
                .register(meterRegistry);

        this.handshakeFailure = Counter.builder("websocket.handshake.failure")
                .description("Total number of failed handshakes")
                .tag("result", "failure")
                .register(meterRegistry);

        // Initialize gauges
        Gauge.builder("websocket.connections.active", activeConnections, AtomicInteger::get)
                .description("Current number of active WebSocket connections")
                .tag("type", "websocket")
                .register(meterRegistry);

        Gauge.builder("websocket.acknowledgments.pending", pendingAcknowledgments, AtomicInteger::get)
                .description("Number of messages pending acknowledgment")
                .tag("type", "websocket")
                .register(meterRegistry);

        // Initialize timers
        this.messageProcessingTimer = Timer.builder("websocket.message.processing.time")
                .description("Time taken to process a WebSocket message")
                .tag("type", "processing")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofSeconds(10))
                .register(meterRegistry);

        this.connectionEstablishmentTimer = Timer.builder("websocket.connection.establishment.time")
                .description("Time taken to establish a WebSocket connection")
                .tag("type", "connection")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        this.handshakeTimer = Timer.builder("websocket.handshake.duration")
                .description("Time taken for WebSocket handshake")
                .tag("type", "handshake")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        this.dispatchLatencyTimer = Timer.builder("message.dispatch.latency")
                .description("Message dispatch latency")
                .tag("type", "dispatch")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofSeconds(30))
                .register(meterRegistry);

        // Initialize distribution summaries
        this.connectionDurationSummary = DistributionSummary.builder("websocket.connection.duration")
                .description("Duration of WebSocket connections")
                .tag("type", "connection")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        this.messageSizeSummary = DistributionSummary.builder("websocket.message.size")
                .description("Size of WebSocket messages in bytes")
                .tag("type", "message")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    // Connection metrics
    public void incrementTotalConnections() {
        totalConnections.increment();
    }

    public void incrementDisconnections() {
        disconnections.increment();
    }

    public void setActiveConnections(int count) {
        activeConnections.set(count);
    }

    public void setPendingAcknowledgments(int count) {
        pendingAcknowledgments.set(count);
    }

    // Message metrics
    public void incrementMessageSent() {
        messagesSent.increment();
        lastMessageTimestamp.set(System.currentTimeMillis());
    }

    public void incrementMessageReceived() {
        messagesReceived.increment();
        lastMessageTimestamp.set(System.currentTimeMillis());
    }

    public void incrementMessageFailed() {
        messagesFailed.increment();
    }

    public void incrementHeartbeatReceived() {
        heartbeatReceived.increment();
    }

    public void incrementTransportErrors() {
        transportErrors.increment();
    }

    public void incrementMessageRetryFailed() {
        messageRetryFailed.increment();
    }

    // Handshake metrics
    public void recordHandshakeSuccess() {
        handshakeSuccess.increment();
    }

    public void recordHandshakeFailure(String reason) {
        handshakeFailure.increment();
        Counter.builder("websocket.handshake.failure.reasons")
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    public void recordHandshakeDuration(long durationMs) {
        handshakeTimer.record(Duration.ofMillis(durationMs));
    }

    // Timer recordings
    public void recordMessageProcessingTime(long durationMs) {
        messageProcessingTimer.record(Duration.ofMillis(durationMs));
    }

    public void recordConnectionEstablishment(long durationMs) {
        connectionEstablishmentTimer.record(Duration.ofMillis(durationMs));
    }

    public void recordDispatchLatency(long durationMs) {
        dispatchLatencyTimer.record(Duration.ofMillis(durationMs));
    }

    public void recordConnectionDuration(long durationMs) {
        connectionDurationSummary.record(durationMs);
    }

    public void recordMessageSize(long sizeBytes) {
        messageSizeSummary.record(sizeBytes);
    }

    // Convenience methods
    public void incrementTotalConnections(int delta) {
        for (int i = 0; i < delta; i++) {
            totalConnections.increment();
        }
    }

    public int getActiveConnectionCount() {
        return activeConnections.get();
    }

    public long getLastMessageTimestamp() {
        return lastMessageTimestamp.get();
    }
}
