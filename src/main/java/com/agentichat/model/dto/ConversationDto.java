package com.agentichat.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for conversation metadata — used in REST API responses.
 * Does NOT include messages (those are loaded separately to avoid giant payloads).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationDto {

    private UUID id;

    @NotBlank
    private String userId;

    @Size(max = 255)
    private String title;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private long messageCount;  // Derived field — not stored in DB, computed on read
}
