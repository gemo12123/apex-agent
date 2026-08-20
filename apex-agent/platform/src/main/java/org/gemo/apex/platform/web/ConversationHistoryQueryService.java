package org.gemo.apex.platform.web;

import com.fasterxml.jackson.core.type.TypeReference;
import java.sql.Timestamp;
import java.util.*;
import org.gemo.apex.common.json.JsonUtils;
import org.gemo.apex.common.message.MessageRole;
import org.gemo.apex.common.message.MessageType;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 面向对话页的只读历史查询；历史事实仅来自 session 与 dialogue_message 两张表。 */
@Service
public class ConversationHistoryQueryService {
    private final JdbcTemplate jdbc;

    public ConversationHistoryQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void initializeSummary(String sessionId, String userId, String agentKey, String query) {
        String summary = firstCodePoints(query == null ? "" : query.trim(), 15);
        if (summary.isEmpty()) {
            return;
        }
        jdbc.update(
                """
                UPDATE apex_agent_session SET session_summary=COALESCE(session_summary, ?), updated_time=?
                WHERE session_id=? AND user_id=? AND agent_key=?
                """,
                summary,
                Timestamp.from(java.time.Instant.now()),
                sessionId,
                userId,
                agentKey);
    }

    public List<SessionHistorySummary> list(String userId) {
        return jdbc.query(
                """
                SELECT session_id,agent_key,session_summary,status,last_active_time
                FROM apex_agent_session WHERE user_id=? ORDER BY last_active_time DESC
                """,
                (rs, row) ->
                        new SessionHistorySummary(
                                rs.getString("session_id"),
                                rs.getString("agent_key"),
                                rs.getString("session_summary"),
                                rs.getString("status"),
                                rs.getTimestamp("last_active_time").toInstant()),
                userId);
    }

    public ConversationHistoryView history(String sessionId, String userId) {
        SessionRow session =
                jdbc.query(
                                """
                                SELECT session_id,agent_key,status FROM apex_agent_session
                                WHERE session_id=? AND user_id=?
                                """,
                                (rs, row) ->
                                        new SessionRow(
                                                rs.getString("session_id"),
                                                rs.getString("agent_key"),
                                                rs.getString("status")),
                                sessionId,
                                userId)
                        .stream()
                        .findFirst()
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Map<Long, MutableTurn> turns = new LinkedHashMap<>();
        Map<String, MutableTool> tools = new HashMap<>();
        jdbc.query(
                """
                SELECT turn_no,sort_no,iteration_no,role,message_type,content,payload
                FROM apex_agent_dialogue_message WHERE session_id=? ORDER BY sort_no
                """,
                rs -> {
                    long turnNo = rs.getLong("turn_no");
                    Integer iterationNo = (Integer) rs.getObject("iteration_no");
                    MessageRole role = MessageRole.valueOf(rs.getString("role"));
                    MessageType messageType = MessageType.valueOf(rs.getString("message_type"));
                    String content = rs.getString("content");
                    Map<String, Object> payload =
                            JsonUtils.fromJson(
                                    rs.getString("payload"), new TypeReference<Map<String, Object>>() {});
                    MutableTurn turn = turns.computeIfAbsent(turnNo, MutableTurn::new);
                    if (role == MessageRole.USER && messageType == MessageType.TEXT && turn.question == null) {
                        turn.question = content == null ? "" : content;
                    } else if (role == MessageRole.ASSISTANT && iterationNo != null) {
                        MutableIteration iteration = turn.iterations.computeIfAbsent(iterationNo, MutableIteration::new);
                        if (content != null && !content.isBlank()) {
                            iteration.blocks.add(BlockData.content(content));
                        }
                        if (messageType == MessageType.TOOL_CALLS) {
                            Object calls = payload.get("toolCalls");
                            if (calls instanceof List<?> values) {
                                for (Object value : values) {
                                    if (!(value instanceof Map<?, ?> raw)) continue;
                                    String callId = String.valueOf(raw.get("toolCallId"));
                                    String toolName = String.valueOf(raw.get("name"));
                                    Map<String, Object> arguments = objectMap(raw.get("arguments"));
                                    Map<String, Object> resolved = objectMap(raw.get("resolvedArguments"));
                                    if (resolved.equals(arguments)) resolved = null;
                                    MutableTool tool = new MutableTool(callId, toolName, arguments, resolved);
                                    iteration.blocks.add(tool);
                                    tools.put(callId, tool);
                                }
                            }
                        }
                    } else if (role == MessageRole.TOOL && messageType == MessageType.TOOL_RESULT) {
                        Object callId = payload.get("toolCallId");
                        MutableTool tool = callId == null ? null : tools.get(String.valueOf(callId));
                        if (tool != null) tool.result = content;
                    }
                },
                sessionId);
        return new ConversationHistoryView(
                session.sessionId,
                session.agentKey,
                session.status,
                turns.values().stream().map(MutableTurn::view).toList());
    }

    private static String firstCodePoints(String value, int max) {
        int end = value.offsetByCodePoints(0, Math.min(value.codePointCount(0, value.length()), max));
        return value.substring(0, end);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private record SessionRow(String sessionId, String agentKey, String status) {}

    private static final class MutableTurn {
        private final long no;
        private String question;
        private final Map<Integer, MutableIteration> iterations = new TreeMap<>();

        private MutableTurn(long no) { this.no = no; }

        private ConversationHistoryView.Turn view() {
            return new ConversationHistoryView.Turn(no, question == null ? "" : question,
                    iterations.values().stream().map(MutableIteration::view).toList());
        }
    }

    private static final class MutableIteration {
        private final int no;
        private final List<BlockData> blocks = new ArrayList<>();

        private MutableIteration(int no) { this.no = no; }

        private ConversationHistoryView.Iteration view() {
            return new ConversationHistoryView.Iteration(no, blocks.stream().map(BlockData::view).toList());
        }
    }

    private sealed interface BlockData permits ContentBlock, MutableTool {
        ConversationHistoryView.Block view();
        static ContentBlock content(String content) { return new ContentBlock(content); }
    }

    private record ContentBlock(String content) implements BlockData {
        @Override public ConversationHistoryView.Block view() {
            return new ConversationHistoryView.Block("content", null, content, null, null, null, null);
        }
    }

    private static final class MutableTool implements BlockData {
        private final String id;
        private final String toolName;
        private final Map<String, Object> arguments;
        private final Map<String, Object> resolvedArguments;
        private String result;

        private MutableTool(String id, String toolName, Map<String, Object> arguments, Map<String, Object> resolvedArguments) {
            this.id = id; this.toolName = toolName; this.arguments = arguments; this.resolvedArguments = resolvedArguments;
        }

        @Override public ConversationHistoryView.Block view() {
            return new ConversationHistoryView.Block("tool", id, null, toolName, arguments, resolvedArguments, result);
        }
    }
}
