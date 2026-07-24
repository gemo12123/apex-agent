CREATE EXTENSION IF NOT EXISTS vector;

CREATE SEQUENCE IF NOT EXISTS agent_turn_no_seq;

CREATE TABLE IF NOT EXISTS agent_turn (
    turn_no BIGINT PRIMARY KEY DEFAULT nextval('agent_turn_no_seq'),
    session_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64),
    agent_key VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    last_trace_no INT NOT NULL DEFAULT 0,
    hook_executions TEXT,
    message_mutations TEXT,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    update_time TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_agent_turn_session
    ON agent_turn(session_id, turn_no);

ALTER TABLE agent_turn
    ADD COLUMN IF NOT EXISTS message_mutations TEXT;

CREATE TABLE IF NOT EXISTS agent_trace (
    turn_no BIGINT NOT NULL,
    trace_no INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    trace_payload TEXT NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    update_time TIMESTAMP NOT NULL,
    PRIMARY KEY (turn_no, trace_no)
);

CREATE INDEX IF NOT EXISTS idx_agent_trace_turn
    ON agent_trace(turn_no, trace_no);

CREATE TABLE IF NOT EXISTS agent_session (
    session_id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    agent_key VARCHAR(64) NOT NULL,
    execution_status VARCHAR(32),
    current_stage VARCHAR(32),
    execution_mode VARCHAR(32),
    last_active_time TIMESTAMP,
    runtime_snapshot TEXT,
    fixed_messages TEXT,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS agent_session_dialogue_message (
    id BIGINT PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    turn_no BIGINT,
    sort_no BIGINT NOT NULL,
    role VARCHAR(32),
    message_type VARCHAR(64),
    content TEXT,
    tool_name VARCHAR(128),
    tool_call_id VARCHAR(128),
    token_count INT,
    message_payload TEXT,
    search_text TEXT,
    search_vector tsvector GENERATED ALWAYS AS (to_tsvector('simple', COALESCE(search_text, ''))) STORED,
    embedding vector(1536),
    compacted BOOLEAN NOT NULL DEFAULT FALSE,
    create_time TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_agent_session_dialogue_message_session_sort
    ON agent_session_dialogue_message(session_id, sort_no);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_session_dialogue_message_session_sort
    ON agent_session_dialogue_message(session_id, sort_no);

CREATE INDEX IF NOT EXISTS idx_agent_session_dialogue_message_search_vector
    ON agent_session_dialogue_message USING GIN(search_vector);

CREATE INDEX IF NOT EXISTS idx_agent_session_dialogue_message_embedding
    ON agent_session_dialogue_message USING hnsw(embedding vector_cosine_ops);

CREATE TABLE IF NOT EXISTS agent_session_dialogue_summary (
    session_id VARCHAR(64) PRIMARY KEY,
    role VARCHAR(32),
    message_type VARCHAR(64),
    content TEXT,
    token_count INT,
    compacted_to_sort_no BIGINT,
    source_turn_no BIGINT,
    version_no BIGINT,
    message_payload TEXT,
    search_text TEXT,
    search_vector tsvector GENERATED ALWAYS AS (to_tsvector('simple', COALESCE(search_text, ''))) STORED,
    embedding vector(1536),
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_agent_session_dialogue_summary_search_vector
    ON agent_session_dialogue_summary USING GIN(search_vector);

CREATE INDEX IF NOT EXISTS idx_agent_session_dialogue_summary_embedding
    ON agent_session_dialogue_summary USING hnsw(embedding vector_cosine_ops);

CREATE TABLE IF NOT EXISTS user_profile_memory (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    agent_key VARCHAR(64) NOT NULL,
    memory_key VARCHAR(128) NOT NULL,
    memory_category VARCHAR(32) NOT NULL,
    title VARCHAR(256),
    content TEXT,
    structured_payload TEXT,
    confidence DECIMAL(5, 4),
    importance INT,
    status VARCHAR(32) NOT NULL,
    source_session_id VARCHAR(64),
    observed_time TIMESTAMP,
    expire_time TIMESTAMP,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_profile_memory_key
    ON user_profile_memory(user_id, agent_key, memory_key);

CREATE TABLE IF NOT EXISTS user_execution_history_memory (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    agent_key VARCHAR(64) NOT NULL,
    topic_key VARCHAR(128) NOT NULL,
    title VARCHAR(256),
    content TEXT,
    time_scope VARCHAR(32),
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    structured_payload TEXT,
    confidence DECIMAL(5, 4),
    last_turn_no BIGINT,
    version_no BIGINT,
    status VARCHAR(32) NOT NULL,
    source_session_id VARCHAR(64),
    observed_time TIMESTAMP,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL,
    search_text TEXT,
    search_vector tsvector GENERATED ALWAYS AS (to_tsvector('simple', COALESCE(search_text, ''))) STORED,
    embedding vector(1536)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_execution_history_session_topic
    ON user_execution_history_memory(user_id, agent_key, source_session_id, topic_key, time_scope);

CREATE INDEX IF NOT EXISTS idx_user_execution_history_memory_search_vector
    ON user_execution_history_memory USING GIN(search_vector);

CREATE INDEX IF NOT EXISTS idx_user_execution_history_memory_embedding
    ON user_execution_history_memory USING hnsw(embedding vector_cosine_ops);

CREATE INDEX IF NOT EXISTS idx_user_execution_history_memory_scope_observed
    ON user_execution_history_memory(user_id, agent_key, observed_time DESC);

CREATE TABLE IF NOT EXISTS agent_experience_memory (
    id VARCHAR(64) PRIMARY KEY,
    agent_key VARCHAR(64) NOT NULL,
    topic_key VARCHAR(128),
    memory_key VARCHAR(128),
    title VARCHAR(256),
    content TEXT,
    structured_payload TEXT,
    confidence DECIMAL(5, 4),
    status VARCHAR(32) NOT NULL,
    source_session_id VARCHAR(64),
    observed_time TIMESTAMP,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_experience_memory_key
    ON agent_experience_memory(agent_key, topic_key, memory_key);

CREATE TABLE IF NOT EXISTS skill_usage_record (
    id VARCHAR(64) PRIMARY KEY,
    agent_key VARCHAR(64) NOT NULL,
    skill_name VARCHAR(128) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    turn_no BIGINT,
    activation_message_sort_no BIGINT NOT NULL,
    created_time TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_skill_usage_record_agent_skill
    ON skill_usage_record(agent_key, skill_name);

CREATE TABLE IF NOT EXISTS skill_experience_memory (
    id VARCHAR(64) PRIMARY KEY,
    agent_key VARCHAR(64) NOT NULL,
    skill_name VARCHAR(128) NOT NULL,
    content TEXT,
    version_no BIGINT NOT NULL,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL
);

SELECT setval(
    'agent_turn_no_seq',
    GREATEST(
        COALESCE((SELECT MAX(turn_no) FROM agent_session_dialogue_message), 0),
        COALESCE((SELECT MAX(turn_no) FROM agent_turn), 0)
    ) + 1,
    false
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_skill_experience_memory_agent_skill
    ON skill_experience_memory(agent_key, skill_name);
