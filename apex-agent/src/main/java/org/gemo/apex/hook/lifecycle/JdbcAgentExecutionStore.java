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
                    (turn_no, session_id, agent_key, user_id, status, last_iteration_no,
                     hook_executions, message_mutations, start_time, end_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (turn_no) DO UPDATE SET
                    status = EXCLUDED.status,
                    last_iteration_no = EXCLUDED.last_iteration_no,
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
                turn.getLastIterationNo(),
                JacksonUtils.toJson(turn.getHookExecutions()),
                JacksonUtils.toJson(turn.getMessageMutations()),
                turn.getStartedAt() != null ? Timestamp.valueOf(turn.getStartedAt()) : null,
                turn.getEndedAt() != null ? Timestamp.valueOf(turn.getEndedAt()) : null);
    }

    @Override
    public void saveIteration(AgentIteration iteration) {
        jdbcTemplate.update("""
                INSERT INTO agent_iteration
                    (turn_no, iteration_no, status, iteration_payload, start_time, end_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (turn_no, iteration_no) DO UPDATE SET
                    status = EXCLUDED.status,
                    iteration_payload = EXCLUDED.iteration_payload,
                    end_time = EXCLUDED.end_time,
                    update_time = CURRENT_TIMESTAMP
                """,
                iteration.getTurnNo(),
                iteration.getIterationNo(),
                iteration.getStatus() != null ? iteration.getStatus().name() : null,
                JacksonUtils.toJson(iteration),
                iteration.getStartedAt() != null ? Timestamp.valueOf(iteration.getStartedAt()) : null,
                iteration.getEndedAt() != null ? Timestamp.valueOf(iteration.getEndedAt()) : null);
    }

    @Override
    public Optional<AgentTurn> findTurn(long turnNo) {
        List<AgentTurn> results = jdbcTemplate.query(
                "SELECT turn_no, session_id, agent_key, user_id, status, last_iteration_no, "
                        + "hook_executions, message_mutations, start_time, end_time "
                        + "FROM agent_turn WHERE turn_no = ?",
                (rs, rowNum) -> AgentTurn.builder()
                        .turnNo(rs.getLong("turn_no"))
                        .sessionId(rs.getString("session_id"))
                        .agentKey(rs.getString("agent_key"))
                        .userId(rs.getString("user_id"))
                        .status(AgentTurn.Status.valueOf(rs.getString("status")))
                        .lastIterationNo(rs.getInt("last_iteration_no"))
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
    public Optional<AgentIteration> findIteration(long turnNo, int iterationNo) {
        List<AgentIteration> results = jdbcTemplate.query(
                "SELECT iteration_payload FROM agent_iteration WHERE turn_no = ? AND iteration_no = ?",
                (rs, rowNum) -> JacksonUtils.fromJson(rs.getString("iteration_payload"), AgentIteration.class),
                turnNo,
                iterationNo);
        return results.stream().findFirst();
    }

    @Override
    public List<AgentIteration> findIterations(long turnNo) {
        return jdbcTemplate.query(
                "SELECT iteration_payload FROM agent_iteration WHERE turn_no = ? ORDER BY iteration_no",
                (rs, rowNum) -> JacksonUtils.fromJson(rs.getString("iteration_payload"), AgentIteration.class),
                turnNo);
    }
}
