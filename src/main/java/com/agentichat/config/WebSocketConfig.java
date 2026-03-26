package com.agentichat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket + STOMP configuration.
 *
 * HOW THE FULL FLOW WORKS:
 *
 * 1. Client connects to: ws://localhost:8080/ws  (the STOMP handshake endpoint)
 * 2. Client subscribes to: /topic/chat/{conversationId}  (to receive AI responses)
 * 3. Client sends a message to: /app/chat.send  (our @MessageMapping handler)
 * 4. Server processes the message, runs the agent, then broadcasts the
 *    response to: /topic/chat/{conversationId}
 * 5. All clients subscribed to that topic receive the message in real time.
 *
 * DESTINATION PREFIXES:
 * - /app  → routes to @MessageMapping methods in our controllers (server-side logic)
 * - /topic → routes to the in-memory broker (pub/sub — one message, many subscribers)
 * - /queue → routes to a specific user's private queue (one-to-one)
 */
@Configuration
@EnableWebSocketMessageBroker  // Tells Spring: activate the STOMP message broker
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Step 1: Register the WebSocket endpoint.
     * This is the URL the client connects to first to establish the WebSocket connection.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry
            .addEndpoint("/ws")               // Client connects to ws://host/ws
            .setAllowedOriginPatterns("*")    // Allow all origins (tighten in production)
            .withSockJS();                    // Enable SockJS fallback for older browsers

        log.info("WebSocket STOMP endpoint registered at /ws");
    }

    /**
     * Step 2: Configure the message broker.
     * The broker is the "post office" — it routes messages between clients and server.
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {

        // Enable an in-memory message broker for these destination prefixes.
        // /topic = broadcast (one → many), e.g., group chat room
        // /queue = private (one → one), e.g., AI response back to specific user
        registry.enableSimpleBroker("/topic", "/queue");

        // Any message sent to a destination starting with /app
        // will be routed to @MessageMapping methods in our controllers.
        // Example: client sends to /app/chat.send → hits @MessageMapping("/chat.send")
        registry.setApplicationDestinationPrefixes("/app");

        // For user-specific messages (sent to a specific user, not a topic).
        // simpMessagingTemplate.convertAndSendToUser(userId, "/queue/reply", message)
        // will deliver to /user/{userId}/queue/reply
        registry.setUserDestinationPrefix("/user");
    }

    /**
     * Step 3: Tune the thread pools for the WebSocket channels.
     *
     * By default Spring uses a small thread pool for WebSocket message processing.
     * Since our agent can take several seconds, we increase the pool
     * to avoid blocking other incoming messages.
     *
     * In production, tune these numbers based on expected concurrent users.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.taskExecutor()
            .corePoolSize(4)      // Always keep 4 threads ready
            .maxPoolSize(10)      // Scale up to 10 under load
            .queueCapacity(100);  // Queue up to 100 messages if all threads busy
    }
}