package com.messagedispatch.handler;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messagedispatch.metrics.MetricsCollector;
import com.messagedispatch.model.SessionInfo;
import com.messagedispatch.model.WebSocketMessage;
import com.messagedispatch.service.MessageDispatcher;
import com.messagedispatch.service.SessionManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket Message Handler
 * 
 * Handles all WebSocket events including:
 * - Connection establishment
 * - Message reception and processing
 * - Connection closure
 * - Error handling
 * 
 * All messages are in JSON format for readability and extensibility.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketMessageHandler extends TextWebSocketHandler {

    private final SessionManager sessionManager;
    private final MessageDispatcher messageDispatcher;
    private final ObjectMapper objectMapper;
    private final MetricsCollector metricsCollector;

    /**
     * Called when a new WebSocket connection is established
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        long startTime = System.nanoTime();
        
        try {
            // Get session info from handshake attributes
            SessionInfo sessionInfo = (SessionInfo) session.getAttributes().get("sessionInfo");
            
            if (sessionInfo == null) {
                log.error("No session info found for connection: {}", session.getId());
                session.close(CloseStatus.NOT_ACCEPTABLE);
                return;
            }

            // Register the session
            sessionManager.registerSession(session, sessionInfo);
            
            // Send connection acknowledgment
            WebSocketMessage ackMessage = WebSocketMessage.builder()
                    .messageId(java.util.UUID.randomUUID().toString())
                    .type(WebSocketMessage.MessageType.SYSTEM)
                    .action("connection.established")
                    .payload(Map.of(
                        "sessionId", session.getId(),
                        "connectedAt", Instant.now().toString(),
                        "message", "Connection established successfully"
                    ))
                    .timestamp(Instant.now())
                    .build();
            
            sendMessage(session, ackMessage);
            
            log.info("WebSocket connection established: sessionId={}, userId={}", 
                    session.getId(), sessionInfo.getUserId());
            
        } finally {
            long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            metricsCollector.recordConnectionEstablishment(duration);
        }
    }

    /**
     * Called when a text message is received from a client
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        long startTime = System.nanoTime();
        SessionInfo sessionInfo = sessionManager.getSession(session.getId()).orElse(null);
        
        if (sessionInfo == null) {
            log.warn("Received message from unknown session: {}", session.getId());
            sendError(session, "Session not found", "SESSION_NOT_FOUND");
            return;
        }

        sessionInfo.updateActivity();
        sessionInfo.incrementMessagesReceived();
        metricsCollector.incrementMessageReceived();

        try {
            // Parse the JSON message
            String payload = message.getPayload();
            WebSocketMessage wsMessage = objectMapper.readValue(payload, WebSocketMessage.class);
            
            log.debug("Received message: type={}, action={}, userId={}", 
                    wsMessage.getType(), wsMessage.getAction(), sessionInfo.getUserId());

            // Handle different message types
            switch (wsMessage.getType()) {
                case HEARTBEAT:
                    handleHeartbeat(session, sessionInfo, wsMessage);
                    break;
                    
                case ACKNOWLEDGMENT:
                    handleAcknowledgment(sessionInfo, wsMessage);
                    break;
                    
                case BROADCAST:
                case UNICAST:
                    handleDataMessage(session, sessionInfo, wsMessage);
                    break;
                    
                case CONTROL:
                    handleControlMessage(session, sessionInfo, wsMessage);
                    break;
                    
                default:
                    sendError(session, "Unknown message type: " + wsMessage.getType(), "UNKNOWN_MESSAGE_TYPE");
            }
            
        } catch (IOException e) {
            log.error("Failed to parse message: {}", e.getMessage());
            sendError(session, "Invalid message format", "INVALID_FORMAT");
            metricsCollector.incrementMessageFailed();
        } catch (Exception e) {
            log.error("Error processing message: {}", e.getMessage(), e);
            sendError(session, "Internal server error", "INTERNAL_ERROR");
            metricsCollector.incrementMessageFailed();
        } finally {
            long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            metricsCollector.recordMessageProcessingTime(duration);
        }
    }

    /**
     * Called when a WebSocket connection is closed
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        SessionInfo sessionInfo = sessionManager.removeSession(session.getId());
        
        if (sessionInfo != null) {
            long connectionDuration = sessionInfo.getConnectionDurationMs();
            
            log.info("WebSocket connection closed: sessionId={}, userId={}, duration={}ms, status={}", 
                    session.getId(), sessionInfo.getUserId(), connectionDuration, status);
            
            metricsCollector.recordConnectionDuration(connectionDuration);
        }
    }

    /**
     * Called when a transport error occurs
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error: sessionId={}, error={}", 
                session.getId(), exception.getMessage(), exception);
        
        SessionInfo sessionInfo = sessionManager.getSession(session.getId()).orElse(null);
        
        if (sessionInfo != null) {
            sessionInfo.setStatus(SessionInfo.ConnectionStatus.ERROR);
            metricsCollector.incrementTransportErrors();
        }

        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    /**
     * Handle heartbeat message
     */
    private void handleHeartbeat(WebSocketSession session, SessionInfo sessionInfo, WebSocketMessage message) 
            throws Exception {
        // Respond with heartbeat acknowledgment
        WebSocketMessage response = WebSocketMessage.builder()
                .messageId(java.util.UUID.randomUUID().toString())
                .type(WebSocketMessage.MessageType.HEARTBEAT)
                .action("heartbeat.response")
                .timestamp(Instant.now())
                .payload(Map.of("serverTime", Instant.now().toString()))
                .build();
        
        sendMessage(session, response);
        metricsCollector.incrementHeartbeatReceived();
    }

    /**
     * Handle acknowledgment message from client
     */
    private void handleAcknowledgment(SessionInfo sessionInfo, WebSocketMessage message) {
        String ackFor = message.getAckFor();
        if (ackFor != null) {
            log.debug("Received acknowledgment from user {} for message: {}", 
                    sessionInfo.getUserId(), ackFor);
            messageDispatcher.processAcknowledgment(sessionInfo.getUserId(), ackFor);
        }
    }

    /**
     * Handle data message (broadcast or unicast)
     */
    private void handleDataMessage(WebSocketSession session, SessionInfo sessionInfo, WebSocketMessage message) 
            throws Exception {
        // Set source if not provided
        if (message.getSource() == null) {
            message.setSource(sessionInfo.getUserId());
        }
        
        // Dispatch the message
        messageDispatcher.dispatch(message);
        
        // Send acknowledgment if required
        if (Boolean.TRUE.equals(message.getRequireAck())) {
            WebSocketMessage ack = WebSocketMessage.acknowledgment(
                    message.getMessageId(), true, "Message received");
            sendMessage(session, ack);
        }
    }

    /**
     * Handle control message
     */
    private void handleControlMessage(WebSocketSession session, SessionInfo sessionInfo, WebSocketMessage message) 
            throws Exception {
        String action = message.getAction();
        
        if (action == null) {
            sendError(session, "Control message requires action", "MISSING_ACTION");
            return;
        }

        switch (action) {
            case "ping":
                sendMessage(session, WebSocketMessage.builder()
                        .type(WebSocketMessage.MessageType.CONTROL)
                        .action("pong")
                        .timestamp(Instant.now())
                        .build());
                break;
                
            case "status":
                sendMessage(session, WebSocketMessage.builder()
                        .type(WebSocketMessage.MessageType.CONTROL)
                        .action("status.response")
                        .payload(Map.of(
                            "connected", true,
                            "userId", sessionInfo.getUserId(),
                            "messagesReceived", sessionInfo.getMessagesReceived().get(),
                            "messagesSent", sessionInfo.getMessagesSent().get(),
                            "connectionDurationMs", sessionInfo.getConnectionDurationMs()
                        ))
                        .timestamp(Instant.now())
                        .build());
                break;
                
            default:
                sendError(session, "Unknown control action: " + action, "UNKNOWN_ACTION");
        }
    }

    /**
     * Send a message to a WebSocket session
     */
    private void sendMessage(WebSocketSession session, WebSocketMessage message) throws Exception {
        if (session.isOpen()) {
            String json = objectMapper.writeValueAsString(message);
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
            metricsCollector.incrementMessageSent();
        }
    }

    /**
     * Send an error message to a WebSocket session
     */
    private void sendError(WebSocketSession session, String errorMessage, String errorCode) throws Exception {
        WebSocketMessage error = WebSocketMessage.error(errorMessage, errorCode);
        sendMessage(session, error);
    }
}
