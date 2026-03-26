package com.agentichat.repository;

import com.agentichat.model.entity.AgentStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for AgentStep entities.
 * Used primarily for auditability and debugging the agent's reasoning.
 */
@Repository
public interface AgentStepRepository extends JpaRepository<AgentStep, UUID> {

    /**
     * Load all steps for a given assistant message, in order.
     * This reconstructs exactly what the agent did to produce that response.
     */
    List<AgentStep> findByMessage_IdOrderByStepNumberAsc(UUID messageId);

    /**
     * Find all steps where the agent called a specific tool.
     * Used for analytics: "how often is web_search called?"
     */
    List<AgentStep> findByToolName(String toolName);

    /**
     * Aggregate: count how many times each tool was used across ALL agent runs.
     * Returns List<Object[]> where each element is [toolName, count].
     *
     * In a real app, expose this via an admin endpoint to monitor tool usage.
     */
    @Query("""
        SELECT a.toolName, COUNT(a) FROM AgentStep a
        WHERE a.toolName IS NOT NULL
        GROUP BY a.toolName
        ORDER BY COUNT(a) DESC
        """)
    List<Object[]> countToolUsageStats();

    /**
     * Find all ERROR steps — useful for a monitoring dashboard
     * to detect when tools are frequently failing.
     */
    @Query("""
        SELECT a FROM AgentStep a
        WHERE a.error = true
        AND a.message.conversation.id = :conversationId
        ORDER BY a.createdAt DESC
        """)
    List<AgentStep> findErrorStepsByConversation(@Param("conversationId") UUID conversationId);
}
