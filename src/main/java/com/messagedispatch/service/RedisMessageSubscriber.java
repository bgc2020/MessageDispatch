package com.messagedispatch.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messagedispatch.model.WebSocketMessage;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis Message Subscriber
 * 
 * Subscribes to Redis Pub/Sub channels to receive messages from other instances.
 * This enables distributed message delivery across multiple application instances.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisMessageSubscriber implements MessageListener {

    @Value("${redis.pubsub.channels.broadcast:message:broadcast}")
    private String broadcastChannel;

    @Value("${redis.pubsub.channels.unicast:message:unicast}")
    private String unicastChannel;

    @Value("${redis.pubsub.channels.system:message:system}")
    private String systemChannel;

    private final RedisMessageListenerContainer redisMessageListenerContainer;
    private final MessageDispatcher messageDispatcher;
    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void subscribe() {
        // Subscribe to all channels
        redisMessageListenerContainer.addMessageListener(this, 
                new ChannelTopic(broadcastChannel));
        redisMessageListenerContainer.addMessageListener(this, 
                new ChannelTopic(unicastChannel));
        redisMessageListenerContainer.addMessageListener(this, 
                new ChannelTopic(systemChannel));
        
        log.info("Subscribed to Redis channels: {}, {}, {}", 
                broadcastChannel, unicastChannel, systemChannel);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());
        String body = new String(message.getBody());
        
        log.debug("Received message from channel: {}, length: {}", channel, body.length());
        
        try {
            WebSocketMessage wsMessage = objectMapper.readValue(body, WebSocketMessage.class);
            
            // Only process locally - don't re-publish to Redis (avoid infinite loop)
            switch (channel) {
                case "message:broadcast":
                    handleLocalBroadcast(wsMessage);
                    break;
                    
                case "message:unicast":
                    handleLocalUnicast(wsMessage);
                    break;
                    
                case "message:system":
                    handleLocalSystem(wsMessage);
                    break;
                    
                default:
                    // Handle custom channels
                    if (channel.startsWith("message:")) {
                        handleLocalBroadcast(wsMessage);
                    }
            }
            
        } catch (Exception e) {
            log.error("Failed to process message from channel: {}, error: {}", 
                    channel, e.getMessage());
        }
    }

    /**
     * Handle broadcast message - deliver to all local connections
     */
    private void handleLocalBroadcast(WebSocketMessage message) {
        int delivered = sessionManager.broadcast(message);
        log.debug("Local broadcast delivered to {} connections", delivered);
    }

    /**
     * Handle unicast message - deliver to target users on this instance
     */
    private void handleLocalUnicast(WebSocketMessage message) {
        if (message.getTargetUsers() == null || message.getTargetUsers().isEmpty()) {
            return;
        }
        
        int delivered = sessionManager.sendToUsers(message.getTargetUsers(), message);
        log.debug("Local unicast delivered to {} connections", delivered);
    }

    /**
     * Handle system message - deliver to all local connections
     */
    private void handleLocalSystem(WebSocketMessage message) {
        int delivered = sessionManager.broadcast(message);
        log.debug("Local system message delivered to {} connections", delivered);
    }
}
