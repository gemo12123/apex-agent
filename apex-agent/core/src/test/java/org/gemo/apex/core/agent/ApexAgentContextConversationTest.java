package org.gemo.apex.core.agent;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.gemo.apex.common.agent.AgentDefinitionSnapshot;
import org.gemo.apex.common.conversation.*;
import org.gemo.apex.common.execution.AgentRequest;
import org.gemo.apex.common.message.AgentMessageEntry;
import org.gemo.apex.common.message.MessageRole;
import org.gemo.apex.common.message.MessageType;
import org.gemo.apex.core.tool.ToolCatalog;
import org.junit.jupiter.api.Test;

class ApexAgentContextConversationTest {
    @Test
    void keepsWindowEqualToFreshDatabaseProjectionAfterAppendAndCompaction() {
        ContextScenario scenario = context();
        ApexAgentContext context = scenario.context();
        AgentMessageEntry toolResult =
                message(
                        "stable-result",
                        context.allocateSortNo(),
                        Instant.parse("2026-08-01T00:01:00Z"));

        context.appendConversation(List.of(toolResult));

        assertEquals(loadWindow(scenario), context.conversationWindow());
        ConversationSummary summary =
                new ConversationSummary(
                        "compaction-1", "累计摘要", 0, 0, 1, Instant.parse("2026-08-01T00:02:00Z"));
        context.compactConversation(
                new ConversationCompactionCommit(
                        "session-1", summary, List.of(toolResult.entryId()), List.of(toolResult)));

        assertEquals(loadWindow(scenario), context.conversationWindow());
        assertEquals(
                List.of(MessageType.SUMMARY, MessageType.TOOL_RESULT),
                context.conversationWindow().messages().stream()
                        .map(AgentMessageEntry::messageType)
                        .toList());
    }

    @Test
    void keepsExistingDatabaseMessageForStableRetryAndDoesNotMutateWindowWhenAppendFails() {
        ContextScenario scenario = context();
        ApexAgentContext context = scenario.context();
        long sortNo = context.allocateSortNo();
        AgentMessageEntry stored =
                message("stable-result", sortNo, Instant.parse("2026-08-01T00:01:00Z"));
        context.appendConversation(List.of(stored));
        AgentMessageEntry retried =
                message("stable-result", sortNo, Instant.parse("2026-08-01T00:02:00Z"));

        context.appendConversation(List.of(retried));

        assertEquals(stored, context.conversationWindow().messages().getLast());
        assertEquals(2, scenario.fixture().conversation.size());
        var beforeFailure = context.conversationWindow();
        scenario.fixture().failToolResultAppend = true;
        AgentMessageEntry failed =
                message(
                        "failed-result",
                        context.allocateSortNo(),
                        Instant.parse("2026-08-01T00:03:00Z"));
        assertThrows(
                IllegalStateException.class, () -> context.appendConversation(List.of(failed)));
        assertEquals(beforeFailure, context.conversationWindow());
    }

    private ContextScenario context() {
        CoreTestFixture fixture = new CoreTestFixture();
        AgentPorts ports = fixture.ports();
        ApexAgent fresh =
                new ApexAgentFactory()
                        .createNew(new AgentRequest("session-1", "demo", "user-1", "你好"), ports);
        ApexAgentContext context =
                new ApexAgentContext(
                        ports,
                        new AgentDefinitionSnapshot(fixture.definition),
                        new ToolCatalog(List.of()),
                        fresh.snapshot(),
                        loadWindow(ports),
                        null,
                        org.gemo.apex.common.shared.SharedDataStores.create(
                                fresh.snapshot().sharedData()));
        return new ContextScenario(fixture, ports, context);
    }

    private ConversationWindow loadWindow(ContextScenario scenario) {
        return loadWindow(scenario.ports());
    }

    private ConversationWindow loadWindow(AgentPorts ports) {
        return ports.windowManager()
                .prepare(new ConversationWindowRequest(new ConversationQuery("session-1")));
    }

    private AgentMessageEntry message(String entryId, long sortNo, Instant createdTime) {
        return new AgentMessageEntry(
                entryId,
                "session-1",
                1,
                sortNo,
                MessageRole.TOOL,
                MessageType.TOOL_RESULT,
                "结果",
                Map.of("toolCallId", "call-1", "toolName", "tool"),
                createdTime);
    }

    private record ContextScenario(
            CoreTestFixture fixture, AgentPorts ports, ApexAgentContext context) {}
}
