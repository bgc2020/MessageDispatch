package com.messagedispatch.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messagedispatch.model.WebSocketMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis Message Publisher
 * 
 * Publishes messages to Redis Pub/Sub channels for distributed message delivery.
 * This enables the system to scale horizontally across multiple instances.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisMessagePublisher {

    @Value("${redis.pubsub.channels.broadcast:message:broadcast}")
    private String broadcastChannel;

    @Value("${redis.pubsub.channels.unicast:message:unicast}")
    private String unicastChannel;

    @Value("${redis.pubsub.channels.system:message:system}")
    private String systemChannel;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Publish a broadcast message to all instances
     */
    public void publishBroadcast(WebSocketMessage message) {
        publish(broadcastChannel, message);
        log.debug("Published broadcast message: messageId={}", message.getMessageId());
    }

    /**
     * Publish a unicast message for specific users
     */
    public void publishUnicast(WebSocketMessage message) {
        publish(unicastChannel, message);
        log.debug("Published unicast message: messageId={}, targets={}", 
                message.getMessageId(), message.getTargetUsers());
    }

    /**
     * Publish a system message
     */
    public void publishSystem(WebSocketMessage message) {
        publish(systemChannel, message);
        log.debug("Published system message: messageId={}", message.getMessageId());
    }

    /**
     * Publish a message to a specific channel
     */
    public void publish(String channel, WebSocketMessage message) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            stringRedisTemplate.convertAndSend(channel, jsonMessage);
            log.trace("Message published to channel: {}", channel);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize message: {}", e.getMessage());
        }
    }

    /**
     * Publish raw message to a custom channel
     */
    public void publishRaw(String channel, String message) {
        stringRedisTemplate.convertAndSend(channel, message);
        log.trace("Raw message published to channel: {}", channel);
    }
}
