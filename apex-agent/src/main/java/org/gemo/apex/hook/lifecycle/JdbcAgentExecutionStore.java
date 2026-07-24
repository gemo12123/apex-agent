package org.gemo.apex.hook.lifecycle;

import com.fasterxml.jackson.core.type.TypeReference;
import org.gemo.apex.util.JacksonUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@ConditionalOnProperty(prefix = "apex.memory.store", name = "type", havingValue = "jdbc")
public class JdbcAgentExecutionStore implements AgentExecutionStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcAgentExecutionStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long nextTurnNo() {
        Long value = jdbcTemplate.queryForObject("SELECT nextval('agent_turn_no_seq')", Long.class);
        if (value == null) {
            throw new IllegalStateException("无法分配全局 turnNo");
        }
        return value;
    }

    @Override
    public void saveTurn(AgentTurn turn) {
        jdbcTemplate.update("""
                INSERT INTO agent_turn
                    (turn_no, session_id, agent_key, user_id, status, last_trace_no,
                     hook_executions, message_mutations, start_time, end_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (turn_no) DO UPDATE SET
                    status = EXCLUDED.status,
                    last_trace_no = EXCLUDED.last_trace_no,
                    hook_executions = EXCLUDED.hook_executions,
                    message_mutations = EXCLUDED.message_mutations,
                    end_time = EXCLUDED.end_time,
                    update_time = CURRENT_TIMESTAMP
                """,
                turn.getTurnNo(),
                turn.getSessionId(),
                turn.getAgentKey(),
                turn.getUserId(),
                turn.getStatus() != null ? turn.getStatus().name() : null,
                turn.getLastTraceNo(),
                JacksonUtils.toJson(turn.getHookExecutions()),
                JacksonUtils.toJson(turn.getMessageMutations()),
                turn.getStartedAt() != null ? Timestamp.valueOf(turn.getStartedAt()) : null,
                turn.getEndedAt() != null ? Timestamp.valueOf(turn.getEndedAt()) : null);
    }

    @Override
    public void saveTrace(AgentTrace trace) {
        jdbcTemplate.update("""
                INSERT INTO agent_trace
                    (turn_no, trace_no, status, trace_payload, start_time, end_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (turn_no, trace_no) DO UPDATE SET
                    status = EXCLUDED.status,
                    trace_payload = EXCLUDED.trace_payload,
                    end_time = EXCLUDED.end_time,
                    update_time = CURRENT_TIMESTAMP
                """,
                trace.getTurnNo(),
                trace.getTraceNo(),
                trace.getStatus() != null ? trace.getStatus().name() : null,
                JacksonUtils.toJson(trace),
                trace.getStartedAt() != null ? Timestamp.valueOf(trace.getStartedAt()) : null,
                trace.getEndedAt() != null ? Timestamp.valueOf(trace.getEndedAt()) : null);
    }

    @Override
    public Optional<AgentTurn> findTurn(long turnNo) {
        List<AgentTurn> results = jdbcTemplate.query(
                "SELECT turn_no, session_id, agent_key, user_id, status, last_trace_no, "
                        + "hook_executions, message_mutations, start_time, end_time "
                        + "FROM agent_turn WHERE turn_no = ?",
                (rs, rowNum) -> AgentTurn.builder()
                        .turnNo(rs.getLong("turn_no"))
                        .sessionId(rs.getString("session_id"))
                        .agentKey(rs.getString("agent_key"))
                        .userId(rs.getString("user_id"))
                        .status(AgentTurn.Status.valueOf(rs.getString("status")))
                        .lastTraceNo(rs.getInt("last_trace_no"))
                        .hookExecutions(readHookExecutions(rs.getString("hook_executions")))
                        .messageMutations(readMessageMutations(rs.getString("message_mutations")))
                        .startedAt(rs.getTimestamp("start_time") != null
                                ? rs.getTimestamp("start_time").toLocalDateTime()
                                : null)
                        .endedAt(rs.getTimestamp("end_time") != null
                                ? rs.getTimestamp("end_time").toLocalDateTime()
                                : null)
                        .build(),
                turnNo);
        return results.stream().findFirst();
    }

    private List<HookExecutionRecord> readHookExecutions(String payload) {
        List<HookExecutionRecord> records = JacksonUtils.fromJson(
                payload, new TypeReference<List<HookExecutionRecord>>() {
                });
        return records != null ? new ArrayList<>(records) : new ArrayList<>();
    }

    private List<MessageMutationRecord> readMessageMutations(String payload) {
        List<MessageMutationRecord> records = JacksonUtils.fromJson(
                payload, new TypeReference<List<MessageMutationRecord>>() {
                });
        return records != null ? new ArrayList<>(records) : new ArrayList<>();
    }

    @Override
    public Optional<AgentTrace> findTrace(long turnNo, int traceNo) {
        List<AgentTrace> results = jdbcTemplate.query(
                "SELECT trace_payload FROM agent_trace WHERE turn_no = ? AND trace_no = ?",
                (rs, rowNum) -> JacksonUtils.fromJson(rs.getString("trace_payload"), AgentTrace.class),
                turnNo,
                traceNo);
        return results.stream().findFirst();
    }

    @Override
    public List<AgentTrace> findTraces(long turnNo) {
        return jdbcTemplate.query(
                "SELECT trace_payload FROM agent_trace WHERE turn_no = ? ORDER BY trace_no",
                (rs, rowNum) -> JacksonUtils.fromJson(rs.getString("trace_payload"), AgentTrace.class),
                turnNo);
    }
}
