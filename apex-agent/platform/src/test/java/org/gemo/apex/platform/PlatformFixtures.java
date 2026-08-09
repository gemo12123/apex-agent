package org.gemo.apex.platform;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.gemo.apex.common.agent.*;
import org.gemo.apex.common.execution.*;
import org.gemo.apex.common.intervention.QuestionInterventionRequest;
import org.gemo.apex.common.intervention.QuestionSpec;
import org.gemo.apex.common.intervention.ToolConfirmationInterventionRequest;
import org.gemo.apex.common.message.AgentMessageEntry;
import org.gemo.apex.common.message.MessageRole;
import org.gemo.apex.common.message.MessageType;
import org.gemo.apex.common.model.ModelRequest;
import org.gemo.apex.common.model.ModelResponse;
import org.gemo.apex.common.snapshot.*;
import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.protocol.event.detail.ToolConfirmationDetail;

public final class PlatformFixtures {
    public static final Instant NOW = Instant.parse("2026-08-01T08:00:00Z");

    private PlatformFixtures() {}

    public static AgentMessageEntry userMessage(String content) {
        return new AgentMessageEntry(
                "entry-1",
                "session-1",
                1,
                0,
                MessageRole.USER,
                MessageType.TEXT,
                content,
                Map.of("nested", List.of("value")),
                NOW);
    }

    public static ToolCall toolCall() {
        return new ToolCall(
                "call-1", "search", 0, Map.of("query", "apex"), Map.of("provider", "fixture"));
    }

    public static SessionSnapshot suspendedSnapshot() {
        AgentDefinitionRecoverySnapshot definition =
                new AgentDefinitionRecoverySnapshot(
                        SnapshotSchemaVersion.V1,
                        new AgentMetadata("default", "Default", "Default agent"),
                        new PromptDefinition("system", 4),
                        new MessageCompressionDefinition(true, 20),
                        Set.of("search"),
                        Set.of(),
                        Map.of(),
                        Map.of());
        ModelRequest request =
                new ModelRequest("system", List.of(userMessage("hello")), List.of(), Map.of());
        ModelResponse response = new ModelResponse(null, List.of(toolCall()), Map.of());
        IterationSnapshot iteration =
                new IterationSnapshot(
                        1, IterationStatus.SUSPENDED, request, response, List.of(), NOW, null);
        TurnSnapshot turn = new TurnSnapshot(1, TurnStatus.SUSPENDED, iteration, NOW, null);
        QuestionInterventionRequest intervention =
                new QuestionInterventionRequest(
                        "call-1",
                        List.of(new QuestionSpec("TEXT_INPUT", "Continue?", null, List.of())));
        PreparedToolCallSnapshot prepared =
                new PreparedToolCallSnapshot(
                        "call-1",
                        "invocation-1",
                        "search",
                        0,
                        toolCall().arguments(),
                        toolCall().metadata(),
                        List.of(),
                        PreparedToolCallDisposition.INTERVENTION,
                        null,
                        intervention,
                        null);
        SuspendedToolBatch suspended = new SuspendedToolBatch("session-1", 1, 1, List.of(prepared));
        return new SessionSnapshot(
                SnapshotSchemaVersion.V1,
                "session-1",
                "user-1",
                "default",
                SessionStatus.HUMAN_IN_THE_LOOP,
                1,
                Set.of("search"),
                Set.of(),
                List.of(),
                definition,
                turn,
                suspended,
                1,
                NOW);
    }

    public static SessionSnapshot confirmationSnapshot() {
        SessionSnapshot source = suspendedSnapshot();
        ToolConfirmationDetail detail =
                ToolConfirmationDetail.builder()
                        .confirmationId("confirmation-1")
                        .toolCallId("call-1")
                        .invocationId("invocation-1")
                        .toolName("search")
                        .toolDisplayName("搜索")
                        .title("确认搜索")
                        .riskLevel("MEDIUM")
                        .editable(false)
                        .confirmLabel("确认")
                        .denyLabel("拒绝")
                        .displayFields(List.of())
                        .editableFields(List.of())
                        .build();
        var intervention =
                new ToolConfirmationInterventionRequest(
                        "call-1", "confirmation-1", "invocation-1", "search", detail, Set.of());
        var original = source.suspendedToolBatch().toolCalls().getFirst();
        var prepared =
                new PreparedToolCallSnapshot(
                        original.toolCallId(),
                        original.invocationId(),
                        original.toolName(),
                        original.ordinal(),
                        original.resolvedArguments(),
                        original.toolCallMetadata(),
                        original.executedPreToolHookIds(),
                        PreparedToolCallDisposition.INTERVENTION,
                        null,
                        intervention,
                        original.submission());
        var suspended =
                new SuspendedToolBatch(
                        source.sessionId(), source.currentTurnNo(), 1, List.of(prepared));
        return new SessionSnapshot(
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
                suspended,
                source.nextMessageSortNo(),
                source.lastActiveTime());
    }
}
