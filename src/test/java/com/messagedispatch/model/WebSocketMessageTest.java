package com.messagedispatch.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for WebSocketMessage model
 */
class WebSocketMessageTest {

    @Test
    void testBroadcastCreation() {
        WebSocketMessage message = WebSocketMessage.broadcast("test-action", Map.of("key", "value"));
        
        assertNotNull(message.getMessageId());
        assertEquals(WebSocketMessage.MessageType.BROADCAST, message.getType());
        assertEquals("test-action", message.getAction());
        assertNotNull(message.getTimestamp());
        assertEquals(WebSocketMessage.Priority.NORMAL, message.getPriority());
    }

    @Test
    void testUnicastCreation() {
        List<String> targets = List.of("user1", "user2");
        WebSocketMessage message = WebSocketMessage.unicast("test-action", Map.of("key", "value"), targets);
        
        assertNotNull(message.getMessageId());
        assertEquals(WebSocketMessage.MessageType.UNICAST, message.getType());
        assertEquals(targets, message.getTargetUsers());
    }

    @Test
    void testAcknowledgmentCreation() {
        WebSocketMessage ack = WebSocketMessage.acknowledgment("original-msg-id", true, "Success");
        
        assertNotNull(ack.getMessageId());
        assertEquals(WebSocketMessage.MessageType.ACKNOWLEDGMENT, ack.getType());
        assertEquals("original-msg-id", ack.getAckFor());
    }

    @Test
    void testHeartbeatCreation() {
        WebSocketMessage heartbeat = WebSocketMessage.heartbeat();
        
        assertNotNull(heartbeat.getMessageId());
        assertEquals(WebSocketMessage.MessageType.HEARTBEAT, heartbeat.getType());
        assertNotNull(heartbeat.getTimestamp());
    }

    @Test
    void testErrorCreation() {
        WebSocketMessage error = WebSocketMessage.error("Something went wrong", "ERR001");
        
        assertNotNull(error.getMessageId());
        assertEquals(WebSocketMessage.MessageType.ERROR, error.getType());
        assertEquals(WebSocketMessage.Priority.HIGH, error.getPriority());
    }

    @Test
    void testBuilderPattern() {
        WebSocketMessage message = WebSocketMessage.builder()
                .messageId("test-id")
                .type(WebSocketMessage.MessageType.BROADCAST)
                .action("custom-action")
                .payload(Map.of("data", "test"))
                .priority(WebSocketMessage.Priority.HIGH)
                .requireAck(true)
                .build();
        
        assertEquals("test-id", message.getMessageId());
        assertEquals(WebSocketMessage.Priority.HIGH, message.getPriority());
        assertTrue(message.getRequireAck());
    }
}
