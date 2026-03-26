package com.agentichat.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Records a single step in the agent's ReAct reasoning loop.
 *
 * THE REACT LOOP (Reason + Act):
 * This is the core pattern of agentic AI. Instead of answering immediately,
 * the agent goes through multiple iterations:
 *
 *   Iteration 1:
 *     THOUGHT:      "The user wants the weather in Paris. I should call get_weather."
 *     ACTION:       calls get_weather tool with input {"city": "Paris"}
 *     OBSERVATION:  "The weather in Paris is 18°C and sunny."
 *
 *   Iteration 2:
 *     THOUGHT:      "I have the weather info. I can now answer the user."
 *     FINAL_ANSWER: "The weather in Paris is 18°C and sunny today!"
 *
 * Each of those 4 things (THOUGHT, ACTION, OBSERVATION, FINAL_ANSWER) is
 * one AgentStep row. For one user message that triggers 2 tool calls,
 * you'd have ~6 AgentStep rows.
 *
 * WHY PERSIST THIS?
 * 1. Full auditability — replay exactly what the agent did
 * 2. Debugging — when the agent gives a wrong answer, see where it went wrong
 * 3. Analytics — which tools are called most often? How many steps on average?
 * 4. Trust — show users WHY the AI gave a certain answer
 */
@Entity
@Table(
    name = "agent_steps",
    indexes = {
        @Index(name = "idx_agent_steps_message_id", columnList = "message_id"),
        @Index(name = "idx_agent_steps_step_type", columnList = "step_type")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentStep {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // Which assistant message produced this step
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    // Step number within this message's agent run (1, 2, 3...)
    // Lets us order steps correctly when replaying
    @Column(name = "step_number", nullable = false)
    private Integer stepNumber;

    // What type of step is this?
    @Enumerated(EnumType.STRING)
    @Column(name = "step_type", nullable = false, length = 30)
    private StepType stepType;

    // The agent's internal reasoning text.
    // e.g., "I need to find the current weather. I'll use the weather tool."
    // This is what the LLM "thinks" before deciding what to do.
    @Column(name = "thought", columnDefinition = "TEXT")
    private String thought;

    // For ACTION steps: which tool did the agent decide to call?
    // e.g., "web_search", "database_query", "calculator"
    @Column(name = "tool_name", length = 100)
    private String toolName;

    // For ACTION steps: what input did the agent pass to the tool?
    // Stored as JSON string, e.g., {"query": "weather in Paris"}
    @Column(name = "tool_input", columnDefinition = "TEXT")
    private String toolInput;

    // For OBSERVATION steps: what did the tool return?
    // Stored as JSON string or plain text
    @Column(name = "tool_output", columnDefinition = "TEXT")
    private String toolOutput;

    // How long did this step take? (milliseconds)
    // Useful for identifying which tools are slow
    @Column(name = "duration_ms")
    private Long durationMs;

    // Did this step succeed, or did a tool throw an error?
    @Column(name = "is_error", nullable = false)
    @Builder.Default
    private boolean error = false;

    // If there was an error, what was it?
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * The four types of steps in the ReAct loop.
     */
    public enum StepType {
        THOUGHT,      // Agent's internal reasoning — "I should call X because..."
        ACTION,       // Agent decides to call a tool
        OBSERVATION,  // The result returned by the tool
        FINAL_ANSWER  // Agent is done — this is the final response to the user
    }
}
