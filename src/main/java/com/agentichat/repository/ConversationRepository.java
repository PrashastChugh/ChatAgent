package com.agentichat.repository;

import com.agentichat.model.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for Conversation.
 *
 * JpaRepository<Conversation, UUID> gives us these for FREE:
 *   save(), findById(), findAll(), delete(), count(), existsById()
 *   + pagination and sorting support
 *
 * We only need to define methods for queries that Spring can't derive
 * from the method name alone.
 */
@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    /**
     * Get all non-deleted conversations for a user, newest first, paginated.
     *
     * Spring generates: SELECT * FROM conversations
     *                   WHERE user_id = ? AND is_deleted = false
     *                   ORDER BY created_at DESC
     *                   LIMIT ? OFFSET ?
     *
     * Pageable lets the caller pass: page number, page size, sort order
     * This is critical — never return ALL rows without pagination.
     */
    Page<Conversation> findByUserIdAndDeletedFalseOrderByCreatedAtDesc(
        String userId,
        Pageable pageable
    );

    /**
     * Find a specific conversation by ID, but only if it belongs to the user
     * AND is not deleted. This prevents user A from accessing user B's data
     * even if they know the UUID.
     */
    Optional<Conversation> findByIdAndUserIdAndDeletedFalse(UUID id, String userId);

    /**
     * Soft delete: update the deleted flag instead of running DELETE.
     *
     * @Modifying = this query changes data (required for UPDATE/DELETE)
     * @Query     = custom JPQL (like SQL but for Java objects, not table names)
     *
     * We use JPQL here instead of a method name because "soft delete"
     * (UPDATE, not DELETE) can't be expressed with method name derivation.
     */
    @Modifying
    @Query("UPDATE Conversation c SET c.deleted = true WHERE c.id = :id AND c.userId = :userId")
    int softDelete(@Param("id") UUID id, @Param("userId") String userId);

    /**
     * Count active (non-deleted) conversations for a user.
     * Used for enforcing limits (e.g., free tier: max 10 conversations).
     */
    long countByUserIdAndDeletedFalse(String userId);
}
