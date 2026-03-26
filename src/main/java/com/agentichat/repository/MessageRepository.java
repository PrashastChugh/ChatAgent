package com.agentichat.repository;

import com.agentichat.model.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for Message entities.
 *
 * The most performance-critical queries here are:
 * 1. Loading the context window for the agent (last N messages)
 * 2. Loading full history for display in the UI
 */
@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

    /**
     * Load ALL messages in a conversation, ordered oldest-first.
     * Used when rendering the full chat history in the UI.
     */
    List<Message> findByConversation_IdOrderByCreatedAtAsc(UUID conversationId);

    /**
     * Load the last N messages for building the agent's memory/context window.
     *
     * WHY LIMIT THE CONTEXT?
     * LLMs have a context window limit (e.g., 128k tokens for GPT-4o).
     * Sending thousands of old messages wastes tokens and money.
     * We send only the most recent N messages as context.
     *
     * The subquery trick:
     * 1. Inner query: get the IDs of the last :limit messages (newest first)
     * 2. Outer query: fetch those messages ordered oldest-first
     * This gives us a "sliding window" of recent messages in chronological order.
     */
    @Query("""
        SELECT m FROM Message m
        WHERE m.id IN (
            SELECT m2.id FROM Message m2
            WHERE m2.conversation.id = :conversationId
            ORDER BY m2.createdAt DESC
            LIMIT :limit
        )
        ORDER BY m.createdAt ASC
        """)
    List<Message> findLastNMessages(
        @Param("conversationId") UUID conversationId,
        @Param("limit") int limit
    );

    /**
     * Count messages in a conversation.
     * Useful for pagination and analytics.
     */
    long countByConversation_Id(UUID conversationId);
}
