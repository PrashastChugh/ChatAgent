package com.agentichat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Thread pool configuration for @Async methods.
 *
 * WHY BOUNDED THREAD POOLS?
 * Unbounded pools allow unlimited thread creation. Under heavy load,
 * you'd create thousands of threads → OutOfMemoryError → app crashes.
 * A bounded pool with a queue rejects overflow gracefully.
 *
 * We define TWO separate pools:
 * - agentExecutor: for running AI agent loops (slow, CPU+network bound)
 * - taskExecutor:  for fast async tasks (logging, notifications)
 *
 * This prevents slow agent tasks from blocking fast tasks.
 * This is called "bulkhead pattern" — isolate failure domains.
 */
@Configuration
@Slf4j
public class AsyncConfig {

    /**
     * Thread pool dedicated to running agent loops.
     * Agent loops call the LLM and tools — they're slow (1-30 seconds).
     * We want them isolated so they don't starve other async operations.
     */
    @Bean(name = "agentExecutor")
    public Executor agentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Always keep 4 threads alive — ready to pick up agent tasks immediately
        executor.setCorePoolSize(4);

        // Under burst load, scale up to 10 threads
        executor.setMaxPoolSize(10);

        // If all 10 threads are busy, queue up to 50 more agent requests
        executor.setQueueCapacity(50);

        // Prefix thread names — critical for reading logs and debugging
        // You'll see: "agent-pool-1", "agent-pool-2" in your logs
        executor.setThreadNamePrefix("agent-pool-");

        // When queue is full and max threads reached, don't silently drop the task.
        // CallerRunsPolicy makes the CALLING thread run the task instead.
        // This applies natural backpressure — slows the caller down rather than losing work.
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());

        // On shutdown, wait for running agent tasks to complete (up to 30s)
        // before the JVM exits. Prevents cutting off an agent mid-thought.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();
        log.info("Agent executor thread pool initialized: core={}, max={}, queue={}",
            executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        return executor;
    }

    /**
     * General-purpose async executor for lightweight tasks:
     * Kafka event publishing, audit logging, notifications, etc.
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("task-pool-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}