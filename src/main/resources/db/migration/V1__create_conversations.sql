-- V1: Create conversations table
-- Flyway runs this ONCE. The filename prefix V1__ is the version number.
-- Never modify a migration file after it's been run — create a new one instead.

CREATE TABLE conversations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     VARCHAR(255) NOT NULL,
    title       VARCHAR(255),
    is_deleted  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Index for: "get all conversations for user X, newest first"
CREATE INDEX idx_conversations_user_id  ON conversations(user_id);
CREATE INDEX idx_conversations_created  ON conversations(created_at DESC);

-- Partial index: only index non-deleted rows
-- Much smaller and faster than a full index when most rows are not deleted
CREATE INDEX idx_conversations_active ON conversations(user_id, created_at DESC)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE conversations IS 'One row per chat session between a user and the AI agent';
COMMENT ON COLUMN conversations.user_id IS 'Identifier of the user who owns this conversation';
COMMENT ON COLUMN conversations.is_deleted IS 'Soft delete flag - never hard delete conversations';
