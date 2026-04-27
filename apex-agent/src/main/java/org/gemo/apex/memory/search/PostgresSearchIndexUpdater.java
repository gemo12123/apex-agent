package org.gemo.apex.memory.search;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "apex.memory.store", name = "type", havingValue = "jdbc")
public class PostgresSearchIndexUpdater {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresSearchIndexUpdater(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void refreshDialogueMessage(long id, String searchText, float[] embedding) {
        jdbcTemplate.update("""
                UPDATE agent_session_dialogue_message
                   SET search_text = :searchText,
                       embedding = CAST(:embedding AS vector)
                 WHERE id = :id
                """, params("id", id, "searchText", searchText, "embedding", PgVectorLiteralFormatter.format(embedding)));
    }

    public void refreshDialogueSummary(String sessionId, String searchText, float[] embedding) {
        jdbcTemplate.update("""
                UPDATE agent_session_dialogue_summary
                   SET search_text = :searchText,
                       embedding = CAST(:embedding AS vector)
                 WHERE session_id = :sessionId
                """, params("sessionId", sessionId, "searchText", searchText,
                "embedding", PgVectorLiteralFormatter.format(embedding)));
    }

    public void refreshExecutionHistory(String id, String searchText, float[] embedding) {
        jdbcTemplate.update("""
                UPDATE user_execution_history_memory
                   SET search_text = :searchText,
                       embedding = CAST(:embedding AS vector)
                 WHERE id = :id
                """, params("id", id, "searchText", searchText, "embedding", PgVectorLiteralFormatter.format(embedding)));
    }

    private MapSqlParameterSource params(String idKey, Object idValue, String searchTextKey, Object searchTextValue,
            String embeddingKey, Object embeddingValue) {
        return new MapSqlParameterSource()
                .addValue(idKey, idValue)
                .addValue(searchTextKey, searchTextValue)
                .addValue(embeddingKey, embeddingValue);
    }
}
