-- V3: Create agent_steps table
-- This table records every step the agent takes in its ReAct reasoning loop.
-- It's the "black box recorder" for the AI — full auditability.

CREATE TABLE agent_steps (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id    UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    step_number   INTEGER NOT NULL,          -- Ordering within one agent run
    step_type     VARCHAR(30) NOT NULL CHECK (step_type IN ('THOUGHT', 'ACTION', 'OBSERVATION', 'FINAL_ANSWER')),
    thought       TEXT,                      -- Agent's reasoning text
    tool_name     VARCHAR(100),              -- Which tool was called (ACTION steps)
    tool_input    TEXT,                      -- JSON input sent to the tool
    tool_output   TEXT,                      -- JSON/text result from the tool
    duration_ms   BIGINT,                    -- How long this step took
    is_error      BOOLEAN NOT NULL DEFAULT FALSE,
    error_message TEXT,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),

    -- Ensure step numbers within a message are unique and ordered
    CONSTRAINT uq_agent_step_number UNIQUE (message_id, step_number)
);

CREATE INDEX idx_agent_steps_message_id ON agent_steps(message_id, step_number ASC);
CREATE INDEX idx_agent_steps_type       ON agent_steps(step_type);
CREATE INDEX idx_agent_steps_tool_name  ON agent_steps(tool_name) WHERE tool_name IS NOT NULL;

COMMENT ON TABLE agent_steps IS 'Full audit trail of every reasoning step the AI agent takes (ReAct loop)';
COMMENT ON COLUMN agent_steps.step_type IS 'THOUGHT=reasoning | ACTION=tool call | OBSERVATION=tool result | FINAL_ANSWER=done';
COMMENT ON COLUMN agent_steps.thought IS 'The raw reasoning text from the LLM before it decides what to do';
COMMENT ON COLUMN agent_steps.tool_input IS 'JSON arguments passed to the tool';
COMMENT ON COLUMN agent_steps.tool_output IS 'Raw result returned by the tool, fed back into the next LLM call';
