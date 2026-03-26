package com.agentichat.model.dto;

import com.agentichat.model.entity.AgentStep.StepType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for exposing agent reasoning steps via the REST API.
 *
 * This is an impressive feature to show in interviews:
 * "I built an endpoint that returns the agent's full reasoning chain —
 * you can see every thought, every tool call, and every observation."
 *
 * Example JSON response:
 * {
 *   "stepNumber": 1,
 *   "stepType": "THOUGHT",
 *   "thought": "The user wants weather in Paris. I should use the weather tool.",
 * },
 * {
 *   "stepNumber": 2,
 *   "stepType": "ACTION",
 *   "toolName": "get_weather",
 *   "toolInput": "{\"city\": \"Paris\"}",
 * },
 * {
 *   "stepNumber": 3,
 *   "stepType": "OBSERVATION",
 *   "toolOutput": "18°C, sunny",
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentStepDto {

    private UUID id;
    private Integer stepNumber;
    private StepType stepType;
    private String thought;
    private String toolName;
    private String toolInput;
    private String toolOutput;
    private Long durationMs;
    private boolean error;
    private String errorMessage;
    private LocalDateTime createdAt;
}
