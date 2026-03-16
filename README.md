# Message Dispatch System

A high-performance, high-availability real-time message dispatch system built on Redis and WebSocket technologies.

## Features

- **High Concurrency**: Supports 50,000+ concurrent WebSocket connections on a single instance
- **User Binding**: Each WebSocket connection is uniquely bound to a user account
- **Message Modes**: Supports both broadcast and unicast messaging patterns
- **JSON Protocol**: All messages use standard JSON format for readability and extensibility
- **Redis Integration**: Distributed message delivery via Redis Pub/Sub
- **Telemetry Support**: Comprehensive metrics via Micrometer/Prometheus
- **Auto-Reconnection**: Automatic failover and reconnection mechanisms
- **Health Monitoring**: Spring Boot Actuator integration with custom health indicators

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Client Layer                              │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐               │
│  │  WebSocket  │ │  WebSocket  │ │  WebSocket  │  ...          │
│  │   Client    │ │   Client    │ │   Client    │               │
│  └──────┬──────┘ └──────┬──────┘ └──────┬──────┘               │
└─────────┼───────────────┼───────────────┼───────────────────────┘
          │               │               │
          └───────────────┴───────────────┘
                          │
┌─────────────────────────────────────────────────────────────────┐
│                    WebSocket Layer                               │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │              WebSocketMessageHandler                       │  │
│  │  - Connection lifecycle management                         │  │
│  │  - Message routing                                         │  │
│  │  - Heartbeat handling                                      │  │
│  └───────────────────────────┬───────────────────────────────┘  │
│                              │                                   │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │          WebSocketHandshakeInterceptor                     │  │
│  │  - User authentication                                     │  │
│  │  - Session binding                                         │  │
│  └───────────────────────────────────────────────────────────┘  │
└──────────────────────────────┬──────────────────────────────────┘
                               │
┌──────────────────────────────┴──────────────────────────────────┐
│                    Service Layer                                 │
│  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐   │
│  │ SessionManager  │ │MessageDispatcher│ │ MetricsCollector│   │
│  │                 │ │                 │ │                 │   │
│  │ - Session store │ │ - Routing       │ │ - Micrometer    │   │
│  │ - User binding  │ │ - Broadcasting  │ │ - Prometheus    │   │
│  │ - Cleanup       │ │ - Unicasting    │ │ - Telemetry     │   │
│  └─────────────────┘ └────────┬────────┘ └─────────────────┘   │
└───────────────────────────────┼─────────────────────────────────┘
                                │
┌───────────────────────────────┴─────────────────────────────────┐
│                      Redis Layer                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                   Redis Pub/Sub                            │  │
│  │  - Broadcast channel: message:broadcast                    │  │
│  │  - Unicast channel: message:unicast                        │  │
│  │  - System channel: message:system                          │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## Requirements

- Java 17+
- Maven 3.8+
- Redis 6.0+

## Quick Start

### 1. Build the Project

```bash
mvn clean package -DskipTests
```

### 2. Start Redis

```bash
docker run -d -p 6379:6379 redis:7-alpine
```

### 3. Run the Application

```bash
java -jar target/message-dispatch-1.0.0-SNAPSHOT.jar
```

Or with Maven:

```bash
mvn spring-boot:run
```

### 4. Connect via WebSocket

Connect to `ws://localhost:8080/ws?userId={yourUserId}`

Example using JavaScript:

```javascript
const ws = new WebSocket('ws://localhost:8080/ws?userId=user123');

ws.onopen = () => {
    console.log('Connected!');
    
    // Send a message
    ws.send(JSON.stringify({
        type: 'BROADCAST',
        action: 'chat',
        payload: { message: 'Hello everyone!' }
    }));
};

ws.onmessage = (event) => {
    console.log('Received:', JSON.parse(event.data));
};

ws.onerror = (error) => {
    console.error('WebSocket error:', error);
};
```

## Message Format

All messages use JSON format:

```json
{
    "messageId": "550e8400-e29b-41d4-a716-446655440000",
    "type": "BROADCAST",
    "action": "notification",
    "payload": {
        "content": "Hello World"
    },
    "metadata": {
        "priority": "high"
    },
    "timestamp": "2024-01-15T10:30:00.000Z",
    "source": "user123",
    "priority": "NORMAL",
    "requireAck": false
}
```

### Message Types

| Type | Description |
|------|-------------|
| `BROADCAST` | Broadcast to all connected users |
| `UNICAST` | Send to specific user(s) |
| `SYSTEM` | System-level notifications |
| `ACKNOWLEDGMENT` | Message acknowledgment |
| `HEARTBEAT` | Connection keepalive |
| `ERROR` | Error notification |
| `CONTROL` | Control commands |

## REST API

### Send Broadcast Message

```bash
POST /api/v1/broadcast
Content-Type: application/json

{
    "action": "notification",
    "payload": { "message": "System maintenance in 5 minutes" },
    "metadata": { "priority": "high" }
}
```

### Send Unicast Message

```bash
POST /api/v1/unicast
Content-Type: application/json

{
    "targetUsers": ["user1", "user2"],
    "action": "notification",
    "payload": { "message": "You have a new notification" }
}
```

### Get Statistics

```bash
GET /api/v1/stats
```

### Get Connected Users

```bash
GET /api/v1/users
```

## Monitoring & Telemetry

### Prometheus Metrics

Access metrics at: `http://localhost:8080/actuator/prometheus`

Key metrics:
- `websocket_connections_active` - Current active connections
- `websocket_messages_sent_total` - Total messages sent
- `websocket_messages_received_total` - Total messages received
- `websocket_message_processing_time` - Message processing latency
- `message_dispatch_latency` - Message dispatch latency

### Health Checks

```bash
GET /actuator/health
```

## Configuration

Key configuration options in `application.yml`:

```yaml
server:
  port: 8080

websocket:
  max-connections: 60000
  heartbeat-interval: 30000
  
spring:
  data:
    redis:
      host: localhost
      port: 6379
      
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

## Performance Tuning

### For 50K Concurrent Connections

1. **Increase file descriptor limits**:
   ```bash
   ulimit -n 100000
   ```

2. **JVM tuning**:
   ```bash
   java -Xms4g -Xmx4g -XX:+UseG1GC -jar message-dispatch.jar
   ```

3. **System tuning**:
   ```bash
   # Increase TCP backlog
   sysctl -w net.core.somaxconn=65535
   sysctl -w net.ipv4.tcp_max_syn_backlog=65535
   ```

## Testing

```bash
# Run all tests
mvn test

# Run with integration tests
mvn verify
```

## License

MIT License
