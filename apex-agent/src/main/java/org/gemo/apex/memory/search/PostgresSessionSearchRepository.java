package org.gemo.apex.memory.search;

import org.gemo.apex.memory.config.MemoryProperties;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Repository
public class PostgresSessionSearchRepository implements SessionSearchRepository {

    private static final RowMapper<SessionSearchHit> ROW_MAPPER = new RowMapper<>() {
        @Override
        public SessionSearchHit mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new SessionSearchHit(
                    rs.getString("source_type"),
                    rs.getString("session_id"),
                    rs.getString("message_id"),
                    (Integer) rs.getObject("turn_no"),
                    (Long) rs.getObject("sort_no"),
                    rs.getString("role"),
                    rs.getDouble("hybrid_score"),
                    new SessionSearchHit.ScoreBreakdown(
                            rs.getDouble("fts_score"),
                            rs.getDouble("vector_score"),
                            rs.getDouble("hybrid_score")),
                    rs.getString("snippet"),
                    rs.getObject("create_time", LocalDateTime.class));
        }
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final MemoryProperties memoryProperties;

    public PostgresSessionSearchRepository(NamedParameterJdbcTemplate jdbcTemplate, MemoryProperties memoryProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.memoryProperties = memoryProperties;
    }

    @Override
    public List<SessionSearchHit> search(SessionSearchQuery query, SessionSearchScope scope, float[] queryEmbedding) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", scope != null ? scope.getUserId() : null)
                .addValue("agentKey", scope != null ? scope.getAgentKey() : null)
                .addValue("sessionId", query.getSessionId())
                .addValue("query", query.getQuery())
                .addValue("ftsConfig", memoryProperties.getSearch().getTextSearchConfig())
                .addValue("limit", Math.max(1, query.getLimit() != null ? query.getLimit() : memoryProperties.getSearch().getDefaultSessionSearchLimit()))
                .addValue("queryEmbedding", PgVectorLiteralFormatter.format(queryEmbedding));

        List<SessionSearchHit> hits = new ArrayList<>();
        if (!Boolean.FALSE.equals(query.getIncludeMessages())) {
            hits.addAll(jdbcTemplate.query(buildMessageSql(query.getSearchMode()), params, ROW_MAPPER));
        }
        if (!Boolean.FALSE.equals(query.getIncludeSummaries())) {
            hits.addAll(jdbcTemplate.query(buildSummarySql(query.getSearchMode()), params, ROW_MAPPER));
        }
        return hits;
    }

    private String buildMessageSql(String searchMode) {
        return baseSql(
                "agent_session_dialogue_message",
                "m.id::text",
                "m.turn_no",
                "m.sort_no",
                "m.role",
                "m.content",
                "m.create_time",
                searchMode);
    }

    private String buildSummarySql(String searchMode) {
        return baseSql(
                "agent_session_dialogue_summary",
                "m.session_id",
                "m.source_turn_no",
                "m.compacted_to_sort_no",
                "m.role",
                "m.content",
                "m.update_time",
                searchMode);
    }

    private String baseSql(String tableName, String messageIdExpr, String turnNoExpr, String sortNoExpr, String roleExpr,
            String snippetExpr, String createTimeExpr, String searchMode) {
        String normalized = searchMode == null ? "hybrid" : searchMode.toLowerCase(Locale.ROOT);
        String ftsMatch = switch (normalized) {
            case "vector" -> "FALSE";
            default -> "m.search_vector @@ websearch_to_tsquery(:ftsConfig, :query)";
        };
        String ftsScore = switch (normalized) {
            case "vector" -> "0";
            default -> "CASE WHEN " + ftsMatch
                    + " THEN ts_rank_cd(m.search_vector, websearch_to_tsquery(:ftsConfig, :query)) ELSE 0 END";
        };
        String vectorScore = switch (normalized) {
            case "fts" -> "0";
            default -> "CASE WHEN :queryEmbedding IS NULL OR m.embedding IS NULL THEN 0 " +
                    "ELSE 1 - (m.embedding <=> CAST(:queryEmbedding AS vector)) END";
        };
        String hybridScore = switch (normalized) {
            case "fts" -> ftsScore;
            case "vector" -> vectorScore;
            default -> "(0.45 * (" + ftsScore + ") + 0.55 * (" + vectorScore + "))";
        };
        String hitFilter = switch (normalized) {
            case "fts" -> "fts_match";
            case "vector" -> "vector_score > 0";
            default -> "fts_match OR vector_score > 0";
        };

        return """
                WITH scored_hits AS (
                    SELECT '%s' AS source_type,
                           s.session_id,
                           %s AS message_id,
                           %s AS turn_no,
                           %s AS sort_no,
                           %s AS role,
                           %s AS snippet,
                           %s AS fts_match,
                           %s AS fts_score,
                           %s AS vector_score,
                           %s AS hybrid_score,
                           %s AS create_time
                      FROM %s m
                      JOIN agent_session s ON s.session_id = m.session_id
                     WHERE s.user_id = :userId
                       AND s.agent_key = :agentKey
                       AND (:sessionId IS NULL OR s.session_id = :sessionId)
                )
                SELECT source_type,
                       session_id,
                       message_id,
                       turn_no,
                       sort_no,
                       role,
                       snippet,
                       fts_score,
                       vector_score,
                       hybrid_score,
                       create_time
                  FROM scored_hits
                 WHERE %s
                 ORDER BY hybrid_score DESC
                 LIMIT :limit
                """.formatted(
                tableName.equals("agent_session_dialogue_message") ? "dialogue_message" : "dialogue_summary",
                messageIdExpr,
                turnNoExpr,
                sortNoExpr,
                roleExpr,
                snippetExpr,
                ftsMatch,
                ftsScore,
                vectorScore,
                hybridScore,
                createTimeExpr,
                tableName,
                hitFilter);
    }
}
