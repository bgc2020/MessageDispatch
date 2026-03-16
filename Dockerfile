# Dockerfile for Message Dispatch System
FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="MessageDispatch Team"
LABEL description="High-performance real-time message dispatch system"
LABEL version="1.0.0"

# Set working directory
WORKDIR /app

# Add non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy the JAR file
COPY target/message-dispatch-1.0.0-SNAPSHOT.jar app.jar

# Set ownership
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8080

# JVM options for production
ENV JAVA_OPTS="-Xms2g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

# Entry point
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
