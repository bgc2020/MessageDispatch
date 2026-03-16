package com.messagedispatch.interceptor;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.messagedispatch.model.SessionInfo;
import com.messagedispatch.service.SessionManager;
import com.messagedispatch.metrics.MetricsCollector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket Handshake Interceptor
 * 
 * Intercepts WebSocket handshake requests to:
 * 1. Extract and validate user identity from request parameters or headers
 * 2. Perform authentication and authorization
 * 3. Bind WebSocket session to user account
 * 4. Collect connection metrics
 * 
 * Required Parameters:
 * - userId: Unique user account identifier (required)
 * - token: Authentication token (optional, based on security requirements)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {

    private static final String USER_ID_PARAM = "userId";
    private static final String TOKEN_PARAM = "token";
    private static final String USER_AGENT_HEADER = "User-Agent";
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_REAL_IP = "X-Real-IP";

    private final SessionManager sessionManager;
    private final MetricsCollector metricsCollector;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, 
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        long startTime = System.nanoTime();
        
        try {
            // Extract user ID from query parameters
            String userId = extractUserId(request);
            if (StringUtils.isBlank(userId)) {
                log.warn("WebSocket handshake rejected: missing userId");
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                metricsCollector.recordHandshakeFailure("missing_user_id");
                return false;
            }

            // Validate user ID format (basic validation)
            if (!isValidUserId(userId)) {
                log.warn("WebSocket handshake rejected: invalid userId format: {}", userId);
                response.setStatusCode(HttpStatus.BAD_REQUEST);
                metricsCollector.recordHandshakeFailure("invalid_user_id");
                return false;
            }

            // Extract authentication token (optional)
            String token = extractToken(request);
            
            // Validate token if provided (can be extended for real auth)
            if (StringUtils.isNotBlank(token) && !validateToken(token, userId)) {
                log.warn("WebSocket handshake rejected: invalid token for user: {}", userId);
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                metricsCollector.recordHandshakeFailure("invalid_token");
                return false;
            }

            // Check if user already has an active connection
            if (sessionManager.isUserConnected(userId)) {
                log.info("User {} already has an active connection, will replace", userId);
                // Optionally disconnect existing session
                sessionManager.disconnectUser(userId, "Replaced by new connection");
            }

            // Extract client information
            String clientIp = extractClientIp(request);
            String userAgent = extractUserAgent(request);

            // Create session info and store in attributes
            SessionInfo sessionInfo = SessionInfo.builder()
                    .userId(userId)
                    .authToken(token)
                    .connectedAt(Instant.now())
                    .lastActivityAt(Instant.now())
                    .status(SessionInfo.ConnectionStatus.CONNECTING)
                    .clientIp(clientIp)
                    .userAgent(userAgent)
                    .build();

            // Store session info in WebSocket attributes for later use
            attributes.put("sessionInfo", sessionInfo);
            attributes.put("userId", userId);
            attributes.put("connectedAt", Instant.now());

            log.info("WebSocket handshake accepted for user: {} from IP: {}", userId, clientIp);
            
            // Record successful handshake
            metricsCollector.recordHandshakeSuccess();
            
            return true;

        } catch (Exception e) {
            log.error("Error during WebSocket handshake", e);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            metricsCollector.recordHandshakeFailure("internal_error");
            return false;
        } finally {
            long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            metricsCollector.recordHandshakeDuration(duration);
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, 
                                WebSocketHandler wsHandler, Exception exception) {
        if (exception != null) {
            log.error("WebSocket handshake failed with exception", exception);
            metricsCollector.recordHandshakeFailure("exception: " + exception.getClass().getSimpleName());
        }
    }

    /**
     * Extract user ID from request parameters
     */
    private String extractUserId(ServerHttpRequest request) {
        if (request.getURI().getQuery() == null) {
            return null;
        }
        
        return request.getURI().getQuery()
                .lines()
                .flatMap(query -> java.util.Arrays.stream(query.split("&")))
                .filter(param -> param.startsWith(USER_ID_PARAM + "="))
                .map(param -> param.substring(USER_ID_PARAM.length() + 1))
                .map(this::decodeUrl)
                .findFirst()
                .orElse(null);
    }

    /**
     * Extract authentication token from request
     */
    private String extractToken(ServerHttpRequest request) {
        // Try query parameter first
        if (request.getURI().getQuery() != null) {
            String tokenFromQuery = request.getURI().getQuery()
                    .lines()
                    .flatMap(query -> java.util.Arrays.stream(query.split("&")))
                    .filter(param -> param.startsWith(TOKEN_PARAM + "="))
                    .map(param -> param.substring(TOKEN_PARAM.length() + 1))
                    .map(this::decodeUrl)
                    .findFirst()
                    .orElse(null);
            
            if (tokenFromQuery != null) {
                return tokenFromQuery;
            }
        }

        // Try header
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        return null;
    }

    /**
     * Extract client IP address
     */
    private String extractClientIp(ServerHttpRequest request) {
        // Try X-Forwarded-For header first (for proxied requests)
        String forwardedFor = request.getHeaders().getFirst(X_FORWARDED_FOR);
        if (StringUtils.isNotBlank(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }

        // Try X-Real-IP header
        String realIp = request.getHeaders().getFirst(X_REAL_IP);
        if (StringUtils.isNotBlank(realIp)) {
            return realIp;
        }

        // Fall back to remote address
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
    }

    /**
     * Extract User-Agent header
     */
    private String extractUserAgent(ServerHttpRequest request) {
        return request.getHeaders().getFirst(USER_AGENT_HEADER);
    }

    /**
     * Validate user ID format
     */
    private boolean isValidUserId(String userId) {
        // Basic validation: alphanumeric with underscore and hyphen, 1-64 characters
        return userId != null && userId.matches("^[a-zA-Z0-9_-]{1,64}$");
    }

    /**
     * Validate authentication token
     * This is a placeholder - implement actual token validation based on your auth system
     */
    private boolean validateToken(String token, String userId) {
        // TODO: Implement actual token validation logic
        // For now, accept any non-empty token
        return StringUtils.isNotBlank(token);
    }

    /**
     * URL decode helper
     */
    private String decodeUrl(String value) {
        try {
            return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }
}
