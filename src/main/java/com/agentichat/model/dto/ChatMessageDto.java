package com.agentichat.model.dto;

import com.agentichat.model.entity.Message.MessageRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for WebSocket chat messages — used for BOTH inbound and outbound.
 *
 * Inbound (client → server):  conversationId + content
 * Outbound (server → client): all fields populated including id, role, createdAt
 *
 * Using one DTO for both directions keeps it simple.
 * In a larger app you'd split into ChatMessageRequest / ChatMessageResponse.
 */
@Data                   // Lombok: generates getters, setters, equals, hashCode, toString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageDto {

    // Null on inbound (new message has no ID yet), populated on outbound
    private UUID id;

    // Which conversation this message belongs to.
    // Client must send this so the server knows where to store the message.
    @NotNull(message = "conversationId is required")
    private UUID conversationId;

    // Who sent it: USER or ASSISTANT.
    // Client sends USER; server responds with ASSISTANT.
    private MessageRole role;

    // The actual message text. Min 1 char, max 10000.
    @NotBlank(message = "content cannot be empty")
    @Size(max = 10000, message = "Message cannot exceed 10000 characters")
    private String content;

    // Set by server on outbound — the client doesn't provide a timestamp
    private LocalDateTime createdAt;

    // True if the agent is still processing.
    // Lets the UI show a loading indicator while the agent is running.
    private boolean processing;

    // Optional error message if something went wrong
    private String errorMessage;
}
