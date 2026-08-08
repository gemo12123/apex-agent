package org.gemo.apex.platform;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class SchemaRulesTest {
    /**
     * migration应只包含三张表和Text快照
     */
    @Test
    void migrationContainsOnlyThreeTablesAndTextSnapshots() throws Exception {
        String sql;
        try (var input = getClass().getResourceAsStream("/db/migration/V1__create_core_agent_tables.sql")) {
            assertNotNull(input);
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
        }
        assertEquals(3, sql.split("create table", -1).length - 1);
        assertFalse(sql.contains("jsonb"));
        assertFalse(sql.contains("apex_agent_turn"));
        assertFalse(sql.contains("apex_agent_iteration"));
        assertFalse(sql.contains("current_iteration_no"));
        assertTrue(sql.contains("unique (session_id, sort_no)"));
    }
}
