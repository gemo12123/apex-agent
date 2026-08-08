package org.gemo.apex.platform.persistence.session;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.gemo.apex.common.snapshot.SessionSnapshot;
import org.gemo.apex.extension.repository.SessionRepository;
import org.gemo.apex.platform.persistence.snapshot.SessionSnapshotTextAdapterV1;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PostgresSessionRepository implements SessionRepository {
    private final JdbcTemplate jdbc;
    private final SessionSnapshotTextAdapterV1 adapter = new SessionSnapshotTextAdapterV1();

    public PostgresSessionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<SessionSnapshot> load(String sessionId) {
        var rows =
                jdbc.query(
                        "SELECT * FROM apex_agent_session WHERE session_id = ?",
                        (rs, row) ->
                                new AgentSessionEntity(
                                        rs.getString("session_id"),
                                        rs.getString("user_id"),
                                        rs.getString("agent_key"),
                                        rs.getString("status"),
                                        rs.getLong("current_turn_no"),
                                        rs.getString("agent_definition_snapshot"),
                                        rs.getString("enabled_tool_names"),
                                        rs.getString("activated_skill_names"),
                                        rs.getString("runtime_snapshot"),
                                        rs.getString("suspended_tool_call"),
                                        rs.getTimestamp("last_active_time").toInstant()),
                        sessionId);
        return rows.stream().findFirst().map(adapter::decode);
    }

    @Override
    @Transactional
    public void save(SessionSnapshot snapshot) {
        AgentSessionEntity entity = adapter.encode(snapshot);
        Instant now = Instant.now();
        jdbc.update(
                """
                INSERT INTO apex_agent_session(session_id,user_id,agent_key,status,current_turn_no,
                    agent_definition_snapshot,enabled_tool_names,activated_skill_names,runtime_snapshot,
                    suspended_tool_call,last_active_time,created_time,updated_time)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT (session_id) DO UPDATE SET user_id=EXCLUDED.user_id,agent_key=EXCLUDED.agent_key,
                    status=EXCLUDED.status,current_turn_no=EXCLUDED.current_turn_no,
                    agent_definition_snapshot=EXCLUDED.agent_definition_snapshot,
                    enabled_tool_names=EXCLUDED.enabled_tool_names,
                    activated_skill_names=EXCLUDED.activated_skill_names,runtime_snapshot=EXCLUDED.runtime_snapshot,
                    suspended_tool_call=EXCLUDED.suspended_tool_call,last_active_time=EXCLUDED.last_active_time,
                    updated_time=EXCLUDED.updated_time
                """,
                entity.sessionId(),
                entity.userId(),
                entity.agentKey(),
                entity.status(),
                entity.currentTurnNo(),
                entity.agentDefinitionSnapshot(),
                entity.enabledToolNames(),
                entity.activatedSkillNames(),
                entity.runtimeSnapshot(),
                entity.suspendedToolCall(),
                Timestamp.from(entity.lastActiveTime()),
                Timestamp.from(now),
                Timestamp.from(now));
    }
}
