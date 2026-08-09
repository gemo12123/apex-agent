package org.gemo.apex.platform.persistence.conversation;

import com.fasterxml.jackson.core.type.TypeReference;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.gemo.apex.common.conversation.*;
import org.gemo.apex.common.json.JsonUtils;
import org.gemo.apex.common.message.AgentMessageEntry;
import org.gemo.apex.common.message.MessageRole;
import org.gemo.apex.common.message.MessageType;
import org.gemo.apex.extension.repository.ConversationRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PostgresConversationRepository implements ConversationRepository {
    private final JdbcTemplate jdbc;

    public PostgresConversationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void append(List<AgentMessageEntry> entries) {
        if (entries.stream().anyMatch(entry -> entry.messageType() == MessageType.SUMMARY)) {
            throw new IllegalArgumentException("SUMMARY 消息不能写入对话消息仓储");
        }
        for (AgentMessageEntry entry : entries) {
            store(entry);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationHistory load(ConversationQuery query) {
        Optional<ConversationSummary> summary = loadSummary(query.sessionId());
        List<AgentMessageEntry> messages =
                jdbc.query(
                        """
                SELECT id,session_id,turn_no,sort_no,role,message_type,content,payload,created_time
                FROM apex_agent_dialogue_message WHERE session_id=? ORDER BY sort_no
                """,
                        (rs, row) ->
                                new AgentMessageEntry(
                                        rs.getString("id"),
                                        rs.getString("session_id"),
                                        rs.getLong("turn_no"),
                                        rs.getLong("sort_no"),
                                        MessageRole.valueOf(rs.getString("role")),
                                        MessageType.valueOf(rs.getString("message_type")),
                                        rs.getString("content"),
                                        JsonUtils.fromJson(
                                                rs.getString("payload"),
                                                new TypeReference<Map<String, Object>>() {}),
                                        rs.getTimestamp("created_time").toInstant()),
                        query.sessionId());
        return new ConversationHistory(query.sessionId(), summary, messages);
    }

    @Override
    @Transactional
    public void compact(ConversationCompactionCommit commit) {
        List<Summary> existing =
                jdbc.query(
                        "SELECT compaction_id,content,payload FROM apex_agent_dialogue_summary WHERE session_id=?",
                        (rs, row) -> new Summary(rs.getString(1), rs.getString(2), rs.getString(3)),
                        commit.sessionId());
        String payload =
                JsonUtils.toJson(
                        new SummaryPayload(
                                commit.summary().sourceStartSortNo(),
                                commit.summary().sourceEndSortNo(),
                                commit.retainedEntryIds(),
                                commit.finalMessages()));
        if (!existing.isEmpty()
                && existing.getFirst().compactionId().equals(commit.summary().compactionId())) {
            if (!existing.getFirst().content().equals(commit.summary().content())
                    || !existing.getFirst().payload().equals(payload)) {
                throw new IllegalStateException(
                        "compactionId 内容冲突: " + commit.summary().compactionId());
            }
            return;
        }
        jdbc.update(
                "UPDATE apex_agent_dialogue_message SET compacted=TRUE WHERE session_id=? AND sort_no BETWEEN ? AND ?",
                commit.sessionId(),
                commit.summary().sourceStartSortNo(),
                commit.summary().sourceEndSortNo());
        for (AgentMessageEntry entry : commit.finalMessages()) {
            store(entry);
            jdbc.update(
                    "UPDATE apex_agent_dialogue_message SET compacted=FALSE WHERE id=?",
                    entry.entryId());
        }
        Instant now = commit.summary().updatedTime();
        jdbc.update(
                """
                INSERT INTO apex_agent_dialogue_summary(session_id,compaction_id,content,payload,
                    compacted_to_sort_no,source_turn_no,created_time,updated_time) VALUES (?,?,?,?,?,?,?,?)
                ON CONFLICT (session_id) DO UPDATE SET compaction_id=EXCLUDED.compaction_id,
                    content=EXCLUDED.content,payload=EXCLUDED.payload,
                    compacted_to_sort_no=EXCLUDED.compacted_to_sort_no,source_turn_no=EXCLUDED.source_turn_no,
                    updated_time=EXCLUDED.updated_time
                """,
                commit.sessionId(),
                commit.summary().compactionId(),
                commit.summary().content(),
                payload,
                commit.summary().sourceEndSortNo(),
                commit.summary().sourceTurnNo(),
                Timestamp.from(now),
                Timestamp.from(now));
    }

    private Optional<ConversationSummary> loadSummary(String sessionId) {
        List<ConversationSummary> summaries =
                jdbc.query(
                        """
                        SELECT compaction_id,content,payload,compacted_to_sort_no,
                            source_turn_no,updated_time
                        FROM apex_agent_dialogue_summary WHERE session_id=?
                        """,
                        (rs, row) -> {
                            SummaryPayload payload =
                                    JsonUtils.fromJson(
                                            rs.getString("payload"), SummaryPayload.class);
                            return new ConversationSummary(
                                    rs.getString("compaction_id"),
                                    rs.getString("content"),
                                    payload.sourceStartSortNo(),
                                    rs.getLong("compacted_to_sort_no"),
                                    rs.getLong("source_turn_no"),
                                    rs.getTimestamp("updated_time").toInstant());
                        },
                        sessionId);
        return summaries.stream().findFirst();
    }

    private void store(AgentMessageEntry entry) {
        String payload = JsonUtils.toJson(entry.payload());
        int changed =
                jdbc.update(
                        """
                INSERT INTO apex_agent_dialogue_message(id,session_id,turn_no,sort_no,role,message_type,
                    content,payload,compacted,created_time) VALUES (?,?,?,?,?,?,?,?,FALSE,?)
                ON CONFLICT DO NOTHING
                """,
                        entry.entryId(),
                        entry.sessionId(),
                        entry.turnNo(),
                        entry.sortNo(),
                        entry.role().name(),
                        entry.messageType().name(),
                        entry.content(),
                        payload,
                        Timestamp.from(entry.createdTime()));
        if (changed == 1) {
            return;
        }
        List<AgentMessageEntry> existing =
                jdbc.query(
                        """
                SELECT id,session_id,turn_no,sort_no,role,message_type,content,payload,created_time
                FROM apex_agent_dialogue_message WHERE id=?
                """,
                        (rs, row) ->
                                new AgentMessageEntry(
                                        rs.getString("id"),
                                        rs.getString("session_id"),
                                        rs.getLong("turn_no"),
                                        rs.getLong("sort_no"),
                                        MessageRole.valueOf(rs.getString("role")),
                                        MessageType.valueOf(rs.getString("message_type")),
                                        rs.getString("content"),
                                        JsonUtils.fromJson(
                                                rs.getString("payload"),
                                                new TypeReference<Map<String, Object>>() {}),
                                        rs.getTimestamp("created_time").toInstant()),
                        entry.entryId());
        if (existing.isEmpty() || !existing.getFirst().equals(entry)) {
            throw new IllegalStateException("消息 entryId 或 sortNo 冲突: " + entry.entryId());
        }
    }

    private record Summary(String compactionId, String content, String payload) {}

    private record SummaryPayload(
            long sourceStartSortNo,
            long sourceEndSortNo,
            List<String> retainedEntryIds,
            List<AgentMessageEntry> finalMessages) {}
}
