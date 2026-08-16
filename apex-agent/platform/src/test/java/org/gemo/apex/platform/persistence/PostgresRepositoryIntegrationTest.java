package org.gemo.apex.platform.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.flywaydb.core.Flyway;
import org.gemo.apex.common.conversation.*;
import org.gemo.apex.common.intervention.HumanResponseCommand;
import org.gemo.apex.common.message.AgentMessageEntry;
import org.gemo.apex.common.message.MessageRole;
import org.gemo.apex.common.message.MessageType;
import org.gemo.apex.common.model.ModelResponse;
import org.gemo.apex.common.shared.SharedDataCleanupPolicy;
import org.gemo.apex.common.shared.SharedDataEntry;
import org.gemo.apex.common.snapshot.SessionSnapshot;
import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.common.tool.ToolDefinition;
import org.gemo.apex.common.tool.ToolExecutionContext;
import org.gemo.apex.common.tool.ToolResult;
import org.gemo.apex.extension.tool.AgentTool;
import org.gemo.apex.extension.tool.ToolExecutionObserver;
import org.gemo.apex.platform.PlatformFixtures;
import org.gemo.apex.platform.persistence.conversation.PostgresConversationRepository;
import org.gemo.apex.platform.persistence.session.PostgresSessionRepository;
import org.gemo.apex.runtime.api.ApexAgentRuntime;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class PostgresRepositoryIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    /** 空库迁移长Text幂等持久化并在进程重建后恢复HumanResponse */
    @Test
    void
            migratesEmptyDatabasePersistsLongTextIdempotentlyAndRestoresHumanResponseAfterProcessRebuild() {
        var dataSource =
                new DriverManagerDataSource(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        var jdbc = new JdbcTemplate(dataSource);
        assertEquals(
                3,
                jdbc.queryForObject(
                        """
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema='public' AND table_name LIKE 'apex_agent_%'
                """,
                        Integer.class));

        var sessionsA = new PostgresSessionRepository(jdbc);
        var conversationsA = new PostgresConversationRepository(jdbc);
        SessionSnapshot source = PlatformFixtures.suspendedSnapshot();
        SessionSnapshot sharedSnapshot =
                new SessionSnapshot(
                        source.schemaVersion(),
                        source.sessionId(),
                        source.userId(),
                        source.agentKey(),
                        source.status(),
                        source.currentTurnNo(),
                        source.enabledTools(),
                        source.activatedSkills(),
                        source.historicalToolBindings(),
                        source.activeDefinition(),
                        source.activeTurn(),
                        source.suspendedToolBatch(),
                        Map.of(
                                "resume-state",
                                new SharedDataEntry(
                                        SharedDataCleanupPolicy.NEVER,
                                        Map.of("phase", "suspended"))),
                        source.nextMessageSortNo(),
                        source.lastActiveTime());
        sessionsA.save(sharedSnapshot);
        String runtimeSnapshot =
                jdbc.queryForObject(
                        "SELECT runtime_snapshot FROM apex_agent_session WHERE session_id='session-1'",
                        String.class);
        assertFalse(runtimeSnapshot.contains("modelRequest"));
        assertFalse(runtimeSnapshot.contains("modelResponse"));
        assertFalse(runtimeSnapshot.contains("hello"));
        assertEquals(
                sharedSnapshot.sharedData(),
                sessionsA.load("session-1").orElseThrow().sharedData());
        String longText = "长内容".repeat(40_000);
        AgentMessageEntry entry =
                new AgentMessageEntry(
                        "long-entry",
                        "session-1",
                        1,
                        0,
                        MessageRole.USER,
                        MessageType.TEXT,
                        longText,
                        Map.of("nested", List.of(longText)),
                        Instant.now());
        conversationsA.append(List.of(entry));
        conversationsA.append(List.of(entry));
        AgentMessageEntry retained =
                new AgentMessageEntry(
                        "retained-entry",
                        "session-1",
                        1,
                        1,
                        MessageRole.ASSISTANT,
                        MessageType.TEXT,
                        "保留消息",
                        Map.of(),
                        Instant.parse("2026-08-01T00:00:00.123456789Z"));
        conversationsA.append(List.of(retained));
        conversationsA.append(
                List.of(
                        new AgentMessageEntry(
                                retained.entryId(),
                                retained.sessionId(),
                                retained.turnNo(),
                                retained.sortNo(),
                                retained.role(),
                                retained.messageType(),
                                retained.content(),
                                retained.payload(),
                                retained.createdTime().plusNanos(1))));
        assertEquals(
                longText,
                conversationsA
                        .load(new ConversationQuery("session-1"))
                        .messages()
                        .getFirst()
                        .content());
        ConversationSummary summary =
                new ConversationSummary("compaction-1", "累计摘要", 0, 0, 1, Instant.now());
        conversationsA.compact(
                new ConversationCompactionCommit("session-1", summary, List.of(retained)));
        AgentMessageEntry hookAppend =
                new AgentMessageEntry(
                        "hook-entry",
                        "session-1",
                        1,
                        2,
                        MessageRole.SYSTEM,
                        MessageType.TEXT,
                        "Hook补充",
                        Map.of(),
                        Instant.now());
        conversationsA.append(List.of(hookAppend));
        ConversationHistory history = conversationsA.load(new ConversationQuery("session-1"));
        assertEquals(summary.content(), history.summary().orElseThrow().content());
        assertEquals(
                List.of("retained-entry", "hook-entry"),
                history.messages().stream().map(AgentMessageEntry::entryId).toList());
        assertEquals(
                3,
                jdbc.queryForObject(
                        "SELECT count(*) FROM apex_agent_dialogue_message WHERE session_id='session-1'",
                        Integer.class));
        assertTrue(
                jdbc.queryForObject(
                        "SELECT compacted FROM apex_agent_dialogue_message WHERE id='long-entry'",
                        Boolean.class));
        assertFalse(
                jdbc.queryForObject(
                        "SELECT compacted FROM apex_agent_dialogue_message WHERE id='retained-entry'",
                        Boolean.class));
        assertFalse(
                jdbc.queryForObject(
                        "SELECT compacted FROM apex_agent_dialogue_message WHERE id='hook-entry'",
                        Boolean.class));
        IllegalStateException entryIdConflict =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                conversationsA.append(
                                        List.of(
                                                new AgentMessageEntry(
                                                        retained.entryId(),
                                                        retained.sessionId(),
                                                        retained.turnNo(),
                                                        retained.sortNo(),
                                                        retained.role(),
                                                        retained.messageType(),
                                                        "冲突内容",
                                                        retained.payload(),
                                                        retained.createdTime()))));
        assertTrue(entryIdConflict.getMessage().contains("entryId 冲突"));
        IllegalStateException sortNoConflict =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                conversationsA.append(
                                        List.of(
                                                new AgentMessageEntry(
                                                        "different-entry",
                                                        retained.sessionId(),
                                                        retained.turnNo(),
                                                        retained.sortNo(),
                                                        retained.role(),
                                                        retained.messageType(),
                                                        retained.content(),
                                                        retained.payload(),
                                                        retained.createdTime()))));
        assertTrue(sortNoConflict.getMessage().contains("sortNo 冲突"));

        try (var processA = runtime(sessionsA, conversationsA, new CopyOnWriteArrayList<>())) {
            assertEquals(0, processA.activeExecutionCount());
        }

        var sessionsB = new PostgresSessionRepository(new JdbcTemplate(dataSource));
        var conversationsB = new PostgresConversationRepository(new JdbcTemplate(dataSource));
        var eventsB = new CopyOnWriteArrayList<>();
        try (var processB = runtime(sessionsB, conversationsB, eventsB)) {
            processB.resumeAgent(
                            new HumanResponseCommand(
                                    "session-1",
                                    "default",
                                    "user-1",
                                    Map.of(
                                            "call-1",
                                            Map.of(
                                                    "interaction_type",
                                                    "ASK_HUMAN",
                                                    "answers",
                                                    Map.of("0", "继续")))))
                    .execute();
            assertEquals("COMPLETED", sessionsB.load("session-1").orElseThrow().status().name());
            assertEquals(
                    1,
                    eventsB.stream()
                            .filter(event -> event.getClass().getSimpleName().equals("EndMessage"))
                            .count());
        }
    }

    private ApexAgentRuntime runtime(
            PostgresSessionRepository sessions,
            PostgresConversationRepository conversations,
            List<Object> events) {
        AgentTool search =
                new AgentTool() {
                    @Override
                    public ToolDefinition definition() {
                        return new ToolDefinition("search", "search", "{}", Map.of());
                    }

                    @Override
                    public ToolResult execute(
                            ToolCall call,
                            ToolExecutionContext context,
                            ToolExecutionObserver observer) {
                        assertEquals(Map.of("provider", "fixture"), call.metadata());
                        assertEquals(Map.of("query", "apex"), call.arguments());
                        return new ToolResult(call.toolCallId(), call.name(), "已恢复", Map.of());
                    }
                };
        return ApexAgentRuntime.builder()
                .modelGateway((request, observer) -> new ModelResponse("完成", List.of(), Map.of()))
                .sessionRepository(sessions)
                .conversationRepository(conversations)
                .registerTool(search)
                .defaultEventPublisherFactory(descriptor -> events::add)
                .build();
    }
}
