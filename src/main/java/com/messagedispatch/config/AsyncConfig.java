package com.messagedispatch.config;

import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Async Configuration
 * 
 * Configures thread pools for async message processing and dispatching.
 * Optimized for high-concurrency scenarios with thousands of connections.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Value("${message.dispatch.thread-pool-size:100}")
    private int dispatchThreadPoolSize;

    @Value("${message.dispatch.queue-capacity:10000}")
    private int queueCapacity;

    /**
     * Configure thread pool for message dispatch operations
     */
    @Bean(name = "messageDispatchExecutor")
    public Executor messageDispatchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(dispatchThreadPoolSize / 2);
        executor.setMaxPoolSize(dispatchThreadPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("msg-dispatch-");
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * Configure thread pool for async Redis operations
     */
    @Bean(name = "redisExecutor")
    public Executor redisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(5000);
        executor.setThreadNamePrefix("redis-");
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);
        executor.initialize();
        return executor;
    }

    /**
     * Configure thread pool for heartbeat and monitoring
     */
    @Bean(name = "heartbeatExecutor")
    public Executor heartbeatExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("heartbeat-");
        executor.initialize();
        return executor;
    }
}
