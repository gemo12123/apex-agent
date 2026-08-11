package org.gemo.apex.common;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.RecordComponent;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.gemo.apex.common.exception.InvalidSnapshotException;
import org.gemo.apex.common.snapshot.*;
import org.gemo.apex.common.tool.CancellationToken;
import org.gemo.apex.common.tool.ToolOrigin;
import org.gemo.apex.common.tool.ToolResult;
import org.junit.jupiter.api.Test;

class SessionSnapshotContractTest {
    /** 历史工具不能回填enabledTools且preHookId不能重复 */
    @Test
    void doesNotBackfillEnabledToolsForHistoricalToolsAndRejectsDuplicatePreHookIds() {
        SessionSnapshot source = CommonFixtures.suspendedSnapshot();
        HistoricalToolBinding historical =
                new HistoricalToolBinding(
                        "search",
                        ToolOrigin.SUB_AGENT,
                        "weather-agent",
                        "DOWN",
                        CommonFixtures.NOW);
        assertThrows(
                InvalidSnapshotException.class,
                () ->
                        new SessionSnapshot(
                                source.schemaVersion(),
                                source.sessionId(),
                                source.userId(),
                                source.agentKey(),
                                source.status(),
                                source.currentTurnNo(),
                                source.enabledTools(),
                                source.activatedSkills(),
                                List.of(historical),
                                source.activeDefinition(),
                                source.activeTurn(),
                                source.suspendedToolBatch(),
                                source.nextMessageSortNo(),
                                source.lastActiveTime()));
        PreparedToolCallSnapshot suspended = source.suspendedToolBatch().toolCalls().getFirst();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PreparedToolCallSnapshot(
                                suspended.toolCallId(),
                                suspended.invocationId(),
                                suspended.toolName(),
                                suspended.ordinal(),
                                suspended.resolvedArguments(),
                                suspended.toolCallMetadata(),
                                List.of("same", "same"),
                                suspended.disposition(),
                                suspended.result(),
                                suspended.intervention(),
                                suspended.submission()));
    }

    @Test
    void rejectsDuplicateBatchCallsMismatchedModelOrderAndConflictingDispositions() {
        SessionSnapshot source = CommonFixtures.suspendedSnapshot();
        PreparedToolCallSnapshot prepared = source.suspendedToolBatch().toolCalls().getFirst();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SuspendedToolBatch(
                                source.sessionId(),
                                source.currentTurnNo(),
                                1,
                                List.of(prepared, prepared)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PreparedToolCallSnapshot(
                                prepared.toolCallId(),
                                prepared.invocationId(),
                                prepared.toolName(),
                                prepared.ordinal(),
                                prepared.resolvedArguments(),
                                prepared.toolCallMetadata(),
                                prepared.executedPreToolHookIds(),
                                PreparedToolCallDisposition.EXECUTE,
                                new ToolResult("call-1", "search", "result", Map.of()),
                                null,
                                null));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PreparedToolCallSnapshot(
                                prepared.toolCallId(),
                                prepared.invocationId(),
                                prepared.toolName(),
                                prepared.ordinal(),
                                prepared.resolvedArguments(),
                                prepared.toolCallMetadata(),
                                prepared.executedPreToolHookIds(),
                                PreparedToolCallDisposition.RETURN_RESULT,
                                null,
                                null,
                                null));

        PreparedToolCallSnapshot wrongOrdinal =
                new PreparedToolCallSnapshot(
                        prepared.toolCallId(),
                        prepared.invocationId(),
                        prepared.toolName(),
                        1,
                        prepared.resolvedArguments(),
                        prepared.toolCallMetadata(),
                        prepared.executedPreToolHookIds(),
                        prepared.disposition(),
                        prepared.result(),
                        prepared.intervention(),
                        prepared.submission());
        SuspendedToolBatch mismatched =
                new SuspendedToolBatch(
                        source.sessionId(), source.currentTurnNo(), 1, List.of(wrongOrdinal));
        assertThrows(
                InvalidSnapshotException.class,
                () ->
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
                                mismatched,
                                source.nextMessageSortNo(),
                                source.lastActiveTime()));
    }

    /** 快照类型图不得包含CancellationToken */
    @Test
    void snapshotTypeGraphExcludesCancellationToken() {
        Set<Class<?>> seen = new HashSet<>();
        ArrayDeque<Class<?>> queue = new ArrayDeque<>();
        queue.add(SessionSnapshot.class);
        while (!queue.isEmpty()) {
            Class<?> type = queue.removeFirst();
            if (!seen.add(type) || !type.isRecord()) {
                continue;
            }
            for (RecordComponent component : type.getRecordComponents()) {
                assertNotEquals(CancellationToken.class, component.getType());
                if (component.getType().getPackageName().startsWith("org.gemo.apex.common")) {
                    queue.add(component.getType());
                }
            }
        }
    }
}
