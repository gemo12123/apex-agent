package org.gemo.apex.memory.search;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PostgresSearchIndexUpdaterTest {

    @Test
    void refreshDialogueMessageShouldWriteSearchTextAndVectorLiteral() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        PostgresSearchIndexUpdater updater = new PostgresSearchIndexUpdater(jdbcTemplate);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);

        updater.refreshDialogueMessage(1L, "search text", new float[] {0.1f, 0.2f});

        verify(jdbcTemplate).update(sqlCaptor.capture(), paramsCaptor.capture());
        assertTrue(sqlCaptor.getValue().contains("agent_session_dialogue_message"));
        assertEquals("search text", paramsCaptor.getValue().getValue("searchText"));
        assertEquals("[0.1,0.2]", paramsCaptor.getValue().getValue("embedding"));
    }
}
