package org.gemo.apex.memory.search;

import org.gemo.apex.memory.config.MemoryProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostgresSessionSearchRepositoryTest {

    @Test
    void ftsModeShouldRequireTsQueryMatchBeforeReturningHits() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
        PostgresSessionSearchRepository repository = new PostgresSessionSearchRepository(jdbcTemplate, properties());
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);

        repository.search(new SessionSearchQuery("find fix", null, 8, "fts", true, false),
                new SessionSearchScope("user-1", "agent-1"), null);

        verify(jdbcTemplate).query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertTrue(sqlCaptor.getValue().contains("m.search_vector @@ websearch_to_tsquery(:ftsConfig, :query)"));
        assertTrue(sqlCaptor.getValue().contains("WHERE fts_match"));
    }

    @Test
    void vectorModeShouldFilterOutZeroScoreRowsWhenQueryEmbeddingMissing() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
        PostgresSessionSearchRepository repository = new PostgresSessionSearchRepository(jdbcTemplate, properties());
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);

        repository.search(new SessionSearchQuery("find fix", null, 8, "vector", false, true),
                new SessionSearchScope("user-1", "agent-1"), null);

        verify(jdbcTemplate).query(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));
        assertTrue(sqlCaptor.getValue().contains("WHERE vector_score > 0"));
        assertEquals(null, paramsCaptor.getValue().getValue("queryEmbedding"));
    }

    @Test
    void hybridModeShouldFilterOutRowsWithoutFtsOrVectorHits() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
        PostgresSessionSearchRepository repository = new PostgresSessionSearchRepository(jdbcTemplate, properties());
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);

        repository.search(new SessionSearchQuery("find fix", null, 8, "hybrid", true, true),
                new SessionSearchScope("user-1", "agent-1"), new float[] {0.1f, 0.2f});

        verify(jdbcTemplate, times(2)).query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertTrue(sqlCaptor.getAllValues().stream().allMatch(sql -> sql.contains("WHERE fts_match OR vector_score > 0")));
    }

    private MemoryProperties properties() {
        MemoryProperties properties = new MemoryProperties();
        properties.getSearch().setTextSearchConfig("simple");
        properties.getSearch().setDefaultSessionSearchLimit(8);
        return properties;
    }
}
