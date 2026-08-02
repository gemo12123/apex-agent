CREATE TABLE apex_agent_session (
    session_id VARCHAR(128) PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    agent_key VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_turn_no BIGINT NOT NULL,
    agent_definition_snapshot TEXT NOT NULL,
    enabled_tool_names TEXT NOT NULL,
    activated_skill_names TEXT NOT NULL,
    runtime_snapshot TEXT NOT NULL,
    suspended_tool_call TEXT NULL,
    last_active_time TIMESTAMPTZ NOT NULL,
    created_time TIMESTAMPTZ NOT NULL,
    updated_time TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_agent_session_owner ON apex_agent_session (user_id, agent_key);

CREATE TABLE apex_agent_dialogue_message (
    id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(128) NOT NULL,
    turn_no BIGINT NOT NULL,
    sort_no BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL,
    message_type VARCHAR(64) NOT NULL,
    content TEXT,
    payload TEXT NOT NULL,
    compacted BOOLEAN NOT NULL DEFAULT FALSE,
    created_time TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_dialogue_session_sort UNIQUE (session_id, sort_no)
);

CREATE INDEX idx_dialogue_message_session ON apex_agent_dialogue_message (session_id, sort_no);

CREATE TABLE apex_agent_dialogue_summary (
    session_id VARCHAR(128) PRIMARY KEY,
    compaction_id VARCHAR(64) NOT NULL,
    content TEXT NOT NULL,
    payload TEXT NOT NULL,
    compacted_to_sort_no BIGINT NOT NULL,
    source_turn_no BIGINT NOT NULL,
    created_time TIMESTAMPTZ NOT NULL,
    updated_time TIMESTAMPTZ NOT NULL
);
