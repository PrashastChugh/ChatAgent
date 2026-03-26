-- V2: Create messages table

CREATE TABLE messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    role            VARCHAR(20) NOT NULL CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    content         TEXT NOT NULL,
    token_count     INTEGER,
    model_used      VARCHAR(100),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Most common query: "get all messages in conversation X, in order"
CREATE INDEX idx_messages_conversation_id ON messages(conversation_id, created_at ASC);

-- For analytics: "how many messages per role?"
CREATE INDEX idx_messages_role ON messages(role);

COMMENT ON TABLE messages IS 'Every message exchanged in a conversation, including system prompts and tool results';
COMMENT ON COLUMN messages.role IS 'USER | ASSISTANT | SYSTEM | TOOL - mirrors LLM API roles';
COMMENT ON COLUMN messages.token_count IS 'Total tokens (input + output) for cost tracking';
COMMENT ON COLUMN messages.model_used IS 'Which LLM model generated this response';
