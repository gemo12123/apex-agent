package org.gemo.apex.platform.persistence;

import org.flywaydb.core.Flyway;
import org.gemo.apex.common.conversation.ConversationQuery;
import org.gemo.apex.common.intervention.HumanResponseCommand;
import org.gemo.apex.common.message.AgentMessageEntry;
import org.gemo.apex.common.message.MessageRole;
import org.gemo.apex.common.message.MessageType;
import org.gemo.apex.common.model.ModelResponse;
import org.gemo.apex.common.tool.ToolDefinition;
import org.gemo.apex.common.tool.ToolResult;
import org.gemo.apex.extension.tool.AgentTool;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class PostgresRepositoryIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    /**
     * 空库迁移长Text幂等持久化并在进程重建后恢复HumanResponse
     */
    @Test
    void migratesEmptyDatabasePersistsLongTextIdempotentlyAndRestoresHumanResponseAfterProcessRebuild() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        assertEquals(3, jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema='public' AND table_name LIKE 'apex_agent_%'
                """, Integer.class));

        var sessionsA = new PostgresSessionRepository(jdbc);
        var conversationsA = new PostgresConversationRepository(jdbc);
        sessionsA.save(PlatformFixtures.suspendedSnapshot());
        String longText = "长内容".repeat(40_000);
        AgentMessageEntry entry = new AgentMessageEntry("long-entry", "session-1", 1, 0,
                MessageRole.USER, MessageType.TEXT, longText, Map.of("nested", List.of(longText)), Instant.now());
        conversationsA.append(List.of(entry));
        conversationsA.append(List.of(entry));
        assertEquals(longText, conversationsA.load(new ConversationQuery("session-1")).getFirst().content());

        try (var processA = runtime(sessionsA, conversationsA, new CopyOnWriteArrayList<>())) {
            assertEquals(0, processA.activeExecutionCount());
        }

        var sessionsB = new PostgresSessionRepository(new JdbcTemplate(dataSource));
        var conversationsB = new PostgresConversationRepository(new JdbcTemplate(dataSource));
        var eventsB = new CopyOnWriteArrayList<>();
        try (var processB = runtime(sessionsB, conversationsB, eventsB)) {
            processB.resumeAgent(new HumanResponseCommand("session-1", "default", "user-1",
                    Map.of("call-1", Map.of("interaction_type", "ASK_HUMAN",
                            "answers", Map.of("0", "继续"))))).execute();
            assertEquals("COMPLETED", sessionsB.load("session-1").orElseThrow().status().name());
            assertEquals(1, eventsB.stream().filter(event ->
                    event.getClass().getSimpleName().equals("EndMessage")).count());
        }
    }

    private ApexAgentRuntime runtime(PostgresSessionRepository sessions,
                                     PostgresConversationRepository conversations,
                                     List<Object> events) {
        AgentTool search = new AgentTool() {
            @Override public ToolDefinition definition() {
                return new ToolDefinition("search", "search", "{}", Map.of());
            }
            @Override public ToolResult execute(org.gemo.apex.common.tool.ToolCall call,
                                                org.gemo.apex.common.tool.ToolExecutionContext context,
                                                org.gemo.apex.extension.tool.ToolExecutionObserver observer) {
                return new ToolResult(call.toolCallId(), call.name(), "已恢复", Map.of());
            }
        };
        return ApexAgentRuntime.builder().modelGateway((request, observer) ->
                        new ModelResponse("完成", List.of(), Map.of()))
                .sessionRepository(sessions).conversationRepository(conversations)
                .registerTool(search).defaultEventPublisherFactory(descriptor -> events::add).build();
    }
}
