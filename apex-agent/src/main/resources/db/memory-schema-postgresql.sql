CREATE TABLE IF NOT EXISTS agent_session (
    session_id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    agent_key VARCHAR(64) NOT NULL,
    execution_status VARCHAR(32),
    current_stage VARCHAR(32),
    execution_mode VARCHAR(32),
    last_active_time TIMESTAMP,
    runtime_snapshot TEXT,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS agent_session_dialogue_message (
    id BIGINT PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    turn_no INT,
    sort_no BIGINT NOT NULL,
    role VARCHAR(32),
    message_type VARCHAR(64),
    content TEXT,
    tool_name VARCHAR(128),
    tool_call_id VARCHAR(128),
    token_count INT,
    message_payload TEXT,
    compacted BOOLEAN NOT NULL DEFAULT FALSE,
    create_time TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_agent_session_dialogue_message_session_sort
    ON agent_session_dialogue_message(session_id, sort_no);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_session_dialogue_message_session_sort
    ON agent_session_dialogue_message(session_id, sort_no);

CREATE TABLE IF NOT EXISTS agent_session_dialogue_summary (
    session_id VARCHAR(64) PRIMARY KEY,
    role VARCHAR(32),
    message_type VARCHAR(64),
    content TEXT,
    token_count INT,
    compacted_to_sort_no BIGINT,
    source_turn_no INT,
    version_no BIGINT,
    message_payload TEXT,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL
);

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
    last_turn_no INT,
    version_no BIGINT,
    status VARCHAR(32) NOT NULL,
    source_session_id VARCHAR(64),
    observed_time TIMESTAMP,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_execution_history_session_topic
    ON user_execution_history_memory(user_id, agent_key, source_session_id, topic_key, time_scope);

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
