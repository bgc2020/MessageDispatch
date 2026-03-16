package com.messagedispatch.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import com.messagedispatch.handler.WebSocketMessageHandler;
import com.messagedispatch.interceptor.WebSocketHandshakeInterceptor;

import lombok.RequiredArgsConstructor;

/**
 * WebSocket Configuration
 * 
 * Configures WebSocket endpoints and handlers for the message dispatch system.
 * Provides performance-tuned settings for high-concurrency scenarios.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final WebSocketMessageHandler webSocketMessageHandler;
    private final WebSocketHandshakeInterceptor webSocketHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketMessageHandler, "/ws")
                .addInterceptors(webSocketHandshakeInterceptor)
                .setAllowedOrigins("*");
    }

    /**
     * Configure WebSocket container for high performance
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        
        // Maximum text message buffer size: 8KB for typical JSON messages
        container.setMaxTextMessageBufferSize(8192);
        
        // Maximum binary message buffer size: 8KB
        container.setMaxBinaryMessageBufferSize(8192);
        
        // Session idle timeout: 60 seconds (should be longer than heartbeat interval)
        container.setMaxSessionIdleTimeout(60000L);
        
        // Async send timeout: 10 seconds
        container.setAsyncSendTimeout(10000L);
        
        return container;
    }
}
