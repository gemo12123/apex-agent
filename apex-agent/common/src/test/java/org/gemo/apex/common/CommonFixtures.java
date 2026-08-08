package org.gemo.apex.common;

import org.gemo.apex.common.agent.*;
import org.gemo.apex.common.execution.*;
import org.gemo.apex.common.hook.HookBinding;
import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.common.intervention.QuestionInterventionRequest;
import org.gemo.apex.common.intervention.QuestionSpec;
import org.gemo.apex.common.message.AgentMessageEntry;
import org.gemo.apex.common.message.MessageRole;
import org.gemo.apex.common.message.MessageType;
import org.gemo.apex.common.model.ModelRequest;
import org.gemo.apex.common.model.ModelResponse;
import org.gemo.apex.common.snapshot.*;
import org.gemo.apex.common.tool.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class CommonFixtures {
    static final Instant NOW = Instant.parse("2026-08-01T08:00:00Z");

    private CommonFixtures() {
    }

    static AgentMessageEntry userMessage() {
        return new AgentMessageEntry("entry-1", "session-1", 1, 0, MessageRole.USER,
                MessageType.TEXT, "hello", Map.of("nested", List.of("value")), NOW);
    }

    static ToolCall toolCall() {
        return new ToolCall("call-1", "search", 0,
                Map.of("query", "apex", "nested", List.of(Map.of("key", "value"))),
                Map.of("vendor", Map.of("requestId", "r-1")));
    }

    static ModelRequest modelRequest() {
        return new ModelRequest("system", List.of(userMessage()),
                List.of(new ToolDefinition("search", "Search", "{\"type\":\"object\"}", Map.of())),
                Map.of("temperature", 0));
    }

    static ModelResponse modelResponse() {
        return new ModelResponse(null, List.of(toolCall()), Map.of("finishReason", "tool_calls"));
    }

    static AgentDefinitionRecoverySnapshot definition() {
        return new AgentDefinitionRecoverySnapshot(SnapshotSchemaVersion.V1,
                new AgentMetadata("default", "Default", "Default agent"),
                new PromptDefinition("system", 4), new MessageCompressionDefinition(true, 20),
                Set.of("search"), Set.of("research"), Map.of(),
                Map.of(HookPoint.PRE_TOOL_CALL,
                        List.of(new HookBinding("confirm", "confirm", 10, true,
                                List.of("search"), Map.of("risk", "medium")))));
    }

    static SessionSnapshot suspendedSnapshot() {
        ToolResult completed = new ToolResult("call-0", "lookup", "done", Map.of());
        IterationSnapshot iteration = new IterationSnapshot(1, IterationStatus.SUSPENDED,
                modelRequest(), modelResponse(), List.of(completed), NOW, null);
        TurnSnapshot turn = new TurnSnapshot(1, TurnStatus.SUSPENDED, iteration, NOW, null);
        QuestionInterventionRequest intervention = new QuestionInterventionRequest("call-1",
                List.of(new QuestionSpec("TEXT_INPUT", "Continue?", null, List.of())));
        PreparedToolCallSnapshot prepared = new PreparedToolCallSnapshot("call-1", "invocation-1",
                "search", 0, toolCall().arguments(), List.of("audit", "confirm"),
                PreparedToolCallDisposition.INTERVENTION, null, intervention, null);
        SuspendedToolBatch suspended = new SuspendedToolBatch("session-1", 1, 1, List.of(prepared));
        return new SessionSnapshot(SnapshotSchemaVersion.V1, "session-1", "user-1", "default",
                SessionStatus.HUMAN_IN_THE_LOOP, 1, Set.of("search"), Set.of("research"), List.of(),
                definition(), turn, suspended, 3, NOW);
    }
}
