package com.agentichat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application entry point.
 *
 * @SpringBootApplication = @Configuration + @EnableAutoConfiguration + @ComponentScan
 * Spring will auto-detect and wire all @Service, @Repository, @Controller, @Component beans.
 *
 * @EnableAsync: allows methods annotated with @Async to run in a separate thread pool.
 * We use this for non-blocking tool execution inside the agent loop.
 *
 * @EnableScheduling: enables @Scheduled tasks (e.g., periodic cache cleanup).
 *
 * @Slf4j (Lombok): generates a static 'log' field — no need to write:
 *   private static final Logger log = LoggerFactory.getLogger(AgenticChatApplication.class);
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
@Slf4j
public class AgenticChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgenticChatApplication.class, args);
    }

    /**
     * Fires once the application context is fully loaded and the app is ready to serve requests.
     * Good place for startup diagnostics.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("=========================================");
        log.info("  Agentic Chat Application is READY");
        log.info("  WebSocket endpoint: /ws");
        log.info("  REST API:           /api/v1");
        log.info("=========================================");
    }
}