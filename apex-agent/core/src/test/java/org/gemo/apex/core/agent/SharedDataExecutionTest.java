package org.gemo.apex.core.agent;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.function.Function;
import org.gemo.apex.common.execution.AgentRequest;
import org.gemo.apex.common.hook.*;
import org.gemo.apex.common.hook.context.*;
import org.gemo.apex.common.hook.operation.*;
import org.gemo.apex.common.hook.result.*;
import org.gemo.apex.common.model.ModelResponse;
import org.gemo.apex.common.shared.SharedDataCleanupPolicy;
import org.gemo.apex.common.shared.SharedDataStore;
import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.common.tool.ToolResult;
import org.gemo.apex.extension.hook.LifecycleHook;
import org.junit.jupiter.api.Test;

class SharedDataExecutionTest {
    @Test
    void sharesOneStoreAcrossAllHooksAndNativeTool() {
        CoreTestFixture fixture = new CoreTestFixture();
        Set<SharedDataStore> identities = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<HookPoint, List<HookBinding>> bindings = new EnumMap<>(HookPoint.class);
        for (HookPoint point : HookPoint.values()) {
            String name = "shared-" + point;
            fixture.hooks.put(
                    name,
                    hook(
                            point,
                            context -> {
                                identities.add(context.sharedData());
                                append(context.sharedData(), point.name());
                                return continued(point, context);
                            }));
            bindings.put(point, List.of(new HookBinding(name, name, 0, true, List.of(), Map.of())));
        }
        fixture.tool(
                "tool",
                (call, context, observer) -> {
                    identities.add(context.sharedData());
                    append(context.sharedData(), "TOOL");
                    return new ToolResult(call.toolCallId(), call.name(), "ok", Map.of());
                });
        fixture.definition = fixture.definition(bindings, Set.of("tool"), Set.of("tool"));
        fixture.compact = true;
        fixture.modelResponses.add(
                new ModelResponse(
                        "",
                        List.of(new ToolCall("call-1", "tool", 0, Map.of(), Map.of())),
                        Map.of()));
        fixture.modelResponses.add(new ModelResponse("完成", List.of(), Map.of()));

        ApexAgent agent =
                new ApexAgentFactory()
                        .createNew(
                                new AgentRequest("session-1", "demo", "user-1", "你好"),
                                fixture.ports());
        assertInstanceOf(AgentRunOutcome.Completed.class, agent.run());

        assertEquals(1, identities.size());
        List<?> sequence =
                assertInstanceOf(List.class, agent.snapshot().sharedData().get("sequence").value());
        assertTrue(sequence.contains("TOOL"));
        for (HookPoint point : HookPoint.values()) {
            assertTrue(sequence.contains(point.name()), point.name());
        }
    }

    @Test
    void cleansEntriesAfterEndHooksAndKeepsNeverEntries() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.hooks.put(
                "build",
                hook(
                        HookPoint.AGENT_BUILD,
                        context -> {
                            context.sharedData()
                                    .put("iteration", 1, SharedDataCleanupPolicy.ITERATION_END);
                            context.sharedData().put("turn", 2, SharedDataCleanupPolicy.TURN_END);
                            context.sharedData().put("session", 3, SharedDataCleanupPolicy.NEVER);
                            return continued(HookPoint.AGENT_BUILD, context);
                        }));
        fixture.hooks.put(
                "iteration-end",
                hook(
                        HookPoint.ITERATION_END,
                        context -> {
                            assertTrue(context.sharedData().containsKey("iteration"));
                            context.sharedData()
                                    .put(
                                            "iteration-from-end",
                                            4,
                                            SharedDataCleanupPolicy.ITERATION_END);
                            return continued(HookPoint.ITERATION_END, context);
                        }));
        fixture.hooks.put(
                "turn-end",
                hook(
                        HookPoint.TURN_END,
                        context -> {
                            assertFalse(context.sharedData().containsKey("iteration"));
                            assertFalse(context.sharedData().containsKey("iteration-from-end"));
                            assertTrue(context.sharedData().containsKey("turn"));
                            context.sharedData()
                                    .put("turn-from-end", 5, SharedDataCleanupPolicy.TURN_END);
                            return continued(HookPoint.TURN_END, context);
                        }));
        fixture.definition =
                fixture.definition(
                        Map.of(
                                HookPoint.AGENT_BUILD, List.of(binding("build")),
                                HookPoint.ITERATION_END, List.of(binding("iteration-end")),
                                HookPoint.TURN_END, List.of(binding("turn-end"))),
                        Set.of(),
                        Set.of());
        fixture.modelResponses.add(new ModelResponse("完成", List.of(), Map.of()));

        ApexAgent agent = newAgent(fixture);
        assertInstanceOf(AgentRunOutcome.Completed.class, agent.run());

        assertEquals(Set.of("session"), agent.snapshot().sharedData().keySet());
    }

    @Test
    void retainsFailedDataUntilNextNormalMatchingBoundaries() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.hooks.put(
                "fail",
                hook(
                        HookPoint.TURN_START,
                        context -> {
                            context.sharedData()
                                    .put("iteration", 1, SharedDataCleanupPolicy.ITERATION_END);
                            context.sharedData().put("turn", 2, SharedDataCleanupPolicy.TURN_END);
                            return new ContinueLoop(
                                    new HookMutations(
                                            List.of(),
                                            new ToolActivationDelta(Set.of("unknown"), Set.of())));
                        }));
        fixture.definition =
                fixture.definition(
                        Map.of(HookPoint.TURN_START, List.of(binding("fail"))), Set.of(), Set.of());

        ApexAgent failed = newAgent(fixture);
        assertInstanceOf(AgentRunOutcome.Failed.class, failed.run());
        assertEquals(Set.of("iteration", "turn"), failed.snapshot().sharedData().keySet());

        fixture.definition = fixture.definition(Map.of(), Set.of(), Set.of());
        fixture.modelResponses.add(new ModelResponse("完成", List.of(), Map.of()));
        ApexAgent recovered = newAgent(fixture);
        assertInstanceOf(AgentRunOutcome.Completed.class, recovered.run());
        assertTrue(recovered.snapshot().sharedData().isEmpty());
    }

    @Test
    void cancellationKeepsShortLivedToolData() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.tool(
                "tool",
                (call, context, observer) -> {
                    context.sharedData().put("iteration", 1, SharedDataCleanupPolicy.ITERATION_END);
                    context.sharedData().put("turn", 2, SharedDataCleanupPolicy.TURN_END);
                    fixture.token.cancel();
                    return new ToolResult(call.toolCallId(), call.name(), "cancelled", Map.of());
                });
        fixture.definition = fixture.definition(Map.of(), Set.of("tool"), Set.of("tool"));
        fixture.modelResponses.add(
                new ModelResponse(
                        "",
                        List.of(new ToolCall("call-1", "tool", 0, Map.of(), Map.of())),
                        Map.of()));

        ApexAgent agent = newAgent(fixture);
        assertInstanceOf(AgentRunOutcome.Cancelled.class, agent.run());
        assertEquals(Set.of("iteration", "turn"), agent.snapshot().sharedData().keySet());
    }

    private ApexAgent newAgent(CoreTestFixture fixture) {
        return new ApexAgentFactory()
                .createNew(new AgentRequest("session-1", "demo", "user-1", "你好"), fixture.ports());
    }

    private HookBinding binding(String name) {
        return new HookBinding(name, name, 0, true, List.of(), Map.of());
    }

    private void append(SharedDataStore store, String value) {
        List<Object> sequence = new ArrayList<>();
        if (store.containsKey("sequence")) {
            sequence.addAll((List<?>) store.get("sequence"));
        }
        sequence.add(value);
        store.put("sequence", sequence, SharedDataCleanupPolicy.NEVER);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private LifecycleHook<?, ?> hook(
            HookPoint point, Function<HookContextView, LifecycleHookResult> action) {
        return new LifecycleHook() {
            @Override
            public String name() {
                return point.name();
            }

            @Override
            public HookTypeDescriptor descriptor() {
                return new HookTypeDescriptor(point, contextType(point), resultType(point));
            }

            @Override
            public LifecycleHookResult apply(HookContextView context) {
                return action.apply(context);
            }
        };
    }

    private LifecycleHookResult continued(HookPoint point, HookContextView context) {
        return switch (point) {
            case AGENT_BUILD -> new ContinueAgentBuild(List.of());
            case TURN_START, ITERATION_START, ITERATION_END ->
                    new ContinueLoop(HookMutations.none());
            case PRE_MESSAGE_COMPRESSION ->
                    new ContinuePreMessageCompression(
                            HookMutations.none(),
                            new ConversationCompactionRequestPatch(
                                    ((PreMessageCompressionContext) context).request()));
            case POST_MESSAGE_COMPRESSION ->
                    new ContinuePostMessageCompression(
                            HookMutations.none(),
                            new ConversationCompactionResultPatch(
                                    ((PostMessageCompressionContext) context).result()));
            case PRE_MODEL_CALL ->
                    new ContinuePreModelCall(
                            HookMutations.none(),
                            new ModelRequestPatch(((PreModelCallContext) context).request()));
            case POST_MODEL_CALL ->
                    new ContinuePostModelCall(
                            HookMutations.none(),
                            new ModelResponsePatch(((PostModelCallContext) context).response()));
            case PRE_TOOL_CALL ->
                    new ContinuePreToolCall(
                            HookMutations.none(),
                            new ToolCallPatch(
                                    ((PreToolCallContext) context).toolCall().arguments()));
            case POST_TOOL_CALL -> {
                ToolResult result = ((PostToolCallContext) context).toolResult();
                yield new ContinuePostToolCall(
                        HookMutations.none(),
                        new ToolResultPatch(result.content(), result.metadata()));
            }
            case TURN_END -> new ContinueTurnEnd();
        };
    }

    private Class<? extends HookContextView> contextType(HookPoint point) {
        return switch (point) {
            case AGENT_BUILD -> AgentBuildContext.class;
            case TURN_START -> TurnStartContext.class;
            case ITERATION_START -> IterationStartContext.class;
            case PRE_MESSAGE_COMPRESSION -> PreMessageCompressionContext.class;
            case POST_MESSAGE_COMPRESSION -> PostMessageCompressionContext.class;
            case PRE_MODEL_CALL -> PreModelCallContext.class;
            case POST_MODEL_CALL -> PostModelCallContext.class;
            case PRE_TOOL_CALL -> PreToolCallContext.class;
            case POST_TOOL_CALL -> PostToolCallContext.class;
            case ITERATION_END -> IterationEndContext.class;
            case TURN_END -> TurnEndContext.class;
        };
    }

    private Class<? extends LifecycleHookResult> resultType(HookPoint point) {
        return switch (point) {
            case AGENT_BUILD -> AgentBuildHookResult.class;
            case TURN_START, ITERATION_START, ITERATION_END -> LoopHookResult.class;
            case PRE_MESSAGE_COMPRESSION -> PreMessageCompressionHookResult.class;
            case POST_MESSAGE_COMPRESSION -> PostMessageCompressionHookResult.class;
            case PRE_MODEL_CALL -> PreModelCallHookResult.class;
            case POST_MODEL_CALL -> PostModelCallHookResult.class;
            case PRE_TOOL_CALL -> PreToolCallHookResult.class;
            case POST_TOOL_CALL -> PostToolCallHookResult.class;
            case TURN_END -> TurnEndHookResult.class;
        };
    }
}
