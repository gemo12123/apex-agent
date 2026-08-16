package org.gemo.apex.core.agent;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.gemo.apex.common.execution.AgentRequest;
import org.gemo.apex.common.hook.HookBinding;
import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.common.hook.HookTypeDescriptor;
import org.gemo.apex.common.hook.context.PostModelCallContext;
import org.gemo.apex.common.hook.context.PreMessageCompressionContext;
import org.gemo.apex.common.hook.context.PreModelCallContext;
import org.gemo.apex.common.hook.context.TurnStartContext;
import org.gemo.apex.common.hook.operation.*;
import org.gemo.apex.common.hook.result.*;
import org.gemo.apex.common.message.AgentMessageEntry;
import org.gemo.apex.common.message.MessageRole;
import org.gemo.apex.common.message.MessageType;
import org.gemo.apex.common.model.ModelResponse;
import org.gemo.apex.extension.hook.LifecycleHook;
import org.junit.jupiter.api.Test;

class PersistentMessageMutationExecutionTest {
    @Test
    void rebuildsPreModelRequestBetweenBindingsAndKeepsMutationVisibleNextTurn() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.tool(
                "search",
                (call, context, observer) ->
                        new org.gemo.apex.common.tool.ToolResult(
                                call.toolCallId(), call.name(), "ok", Map.of()));
        AtomicBoolean secondBindingSawAppend = new AtomicBoolean();
        fixture.hooks.put(
                "append",
                preModelHook(
                        context ->
                                new ContinuePreModelCall(
                                        mutations(
                                                new AppendMessage(
                                                        "append-context",
                                                        MessageRole.SYSTEM,
                                                        MessageType.TEXT,
                                                        "第一版上下文",
                                                        Map.of("kind", "test"))))));
        fixture.hooks.put(
                "replace-and-enable",
                preModelHook(
                        context -> {
                            AgentMessageEntry appended =
                                    context.request().messages().stream()
                                            .filter(message -> "第一版上下文".equals(message.content()))
                                            .findFirst()
                                            .orElseThrow();
                            secondBindingSawAppend.set(true);
                            return new ContinuePreModelCall(
                                    new HookMutations(
                                            List.of(
                                                    new ReplaceMessage(
                                                            "replace-context",
                                                            appended.entryId(),
                                                            MessageRole.SYSTEM,
                                                            MessageType.TEXT,
                                                            "最终上下文",
                                                            Map.of("kind", "test"))),
                                            new ToolActivationDelta(Set.of("search"), Set.of())));
                        }));
        fixture.definition =
                fixture.definition(
                        Map.of(
                                HookPoint.PRE_MODEL_CALL,
                                List.of(binding("append", 0), binding("replace-and-enable", 10))),
                        Set.of("search"),
                        Set.of());
        fixture.modelResponses.add(new ModelResponse("第一轮完成", List.of(), Map.of()));

        AgentRunOutcome first = create(fixture, "第一轮问题").run();

        assertInstanceOf(AgentRunOutcome.Completed.class, first);
        assertTrue(secondBindingSawAppend.get());
        assertTrue(
                fixture.modelRequests.getFirst().messages().stream()
                        .anyMatch(message -> "最终上下文".equals(message.content())));
        assertEquals(
                List.of("search"),
                fixture.modelRequests.getFirst().tools().stream()
                        .map(tool -> tool.name())
                        .toList());
        AgentMessageEntry stored =
                fixture.conversation.stream()
                        .filter(message -> "最终上下文".equals(message.content()))
                        .findFirst()
                        .orElseThrow();
        assertEquals(MessageRole.SYSTEM, stored.role());

        fixture.definition = fixture.definition(Map.of(), Set.of("search"), Set.of());
        fixture.modelResponses.add(new ModelResponse("第二轮完成", List.of(), Map.of()));
        AgentRunOutcome second = create(fixture, "第二轮问题").run();

        assertInstanceOf(AgentRunOutcome.Completed.class, second);
        assertTrue(
                fixture.modelRequests.getLast().messages().stream()
                        .anyMatch(message -> "最终上下文".equals(message.content())));
    }

    @Test
    void postModelMutationDoesNotChangeSentSnapshotButIsVisibleNextTurn() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.hooks.put(
                "post-append",
                postModelHook(
                        context ->
                                new ContinuePostModelCall(
                                        mutations(
                                                new AppendMessage(
                                                        "post-append",
                                                        MessageRole.SYSTEM,
                                                        MessageType.TEXT,
                                                        "模型调用后追加",
                                                        Map.of())),
                                        new ModelResponsePatch(context.response()))));
        fixture.definition =
                fixture.definition(
                        Map.of(HookPoint.POST_MODEL_CALL, List.of(binding("post-append", 0))),
                        Set.of(),
                        Set.of());
        fixture.modelResponses.add(new ModelResponse("第一轮完成", List.of(), Map.of()));

        assertInstanceOf(AgentRunOutcome.Completed.class, create(fixture, "第一轮问题").run());

        assertEquals(
                List.of("第一轮问题"),
                fixture.modelRequests.getFirst().messages().stream()
                        .map(AgentMessageEntry::content)
                        .toList());
        assertTrue(
                fixture.conversation.stream()
                        .anyMatch(message -> "模型调用后追加".equals(message.content())));

        fixture.definition = fixture.definition(Map.of(), Set.of(), Set.of());
        fixture.modelResponses.add(new ModelResponse("第二轮完成", List.of(), Map.of()));
        assertInstanceOf(AgentRunOutcome.Completed.class, create(fixture, "第二轮问题").run());
        assertTrue(
                fixture.modelRequests.getLast().messages().stream()
                        .anyMatch(message -> "模型调用后追加".equals(message.content())));
    }

    @Test
    void preCompressionMutationCanCancelCompressionAfterThresholdRecalculation() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.compact = true;
        fixture.hooks.put(
                "remove-and-cancel",
                preCompressionHook(
                        context -> {
                            fixture.compact = false;
                            AgentMessageEntry target =
                                    context.request().sourceMessages().getFirst();
                            return new ContinuePreMessageCompression(
                                    mutations(
                                            new RemoveMessage(
                                                    "remove-before-compression", target.entryId())),
                                    new ConversationCompactionRequestPatch(context.request()));
                        }));
        fixture.definition =
                fixture.definition(
                        Map.of(
                                HookPoint.PRE_MESSAGE_COMPRESSION,
                                List.of(binding("remove-and-cancel", 0))),
                        Set.of(),
                        Set.of());
        fixture.modelResponses.add(new ModelResponse("完成", List.of(), Map.of()));

        AgentRunOutcome outcome = create(fixture, "会被移除").run();

        assertInstanceOf(AgentRunOutcome.Completed.class, outcome);
        assertNull(fixture.compactionCommit);
        assertNull(fixture.compactionRequest);
        assertEquals(List.of(), fixture.modelRequests.getFirst().messages());
        assertTrue(
                fixture.conversation.stream()
                        .noneMatch(message -> "会被移除".equals(message.content())));
    }

    @Test
    void appliesWholeToolGroupRemovalAndRejectsOrphaningBatchAtomically() {
        CoreTestFixture success = fixtureWithHistoricalToolGroup();
        success.hooks.put(
                "remove-group",
                turnStartHook(
                        context ->
                                new ContinueLoop(
                                        new HookMutations(
                                                List.of(
                                                        new RemoveMessage(
                                                                "remove-call", "historical-call"),
                                                        new RemoveMessage(
                                                                "remove-result",
                                                                "historical-result")),
                                                ToolActivationDelta.none()))));
        success.definition =
                success.definition(
                        Map.of(HookPoint.TURN_START, List.of(binding("remove-group", 0))),
                        Set.of(),
                        Set.of());
        success.modelResponses.add(new ModelResponse("完成", List.of(), Map.of()));

        assertInstanceOf(AgentRunOutcome.Completed.class, create(success, "新问题").run());
        assertTrue(
                success.conversation.stream()
                        .noneMatch(message -> message.entryId().startsWith("historical-")));

        CoreTestFixture failure = fixtureWithHistoricalToolGroup();
        failure.hooks.put(
                "remove-result-only",
                turnStartHook(
                        context ->
                                new ContinueLoop(
                                        mutations(
                                                new RemoveMessage(
                                                        "remove-result", "historical-result")))));
        failure.definition =
                failure.definition(
                        Map.of(HookPoint.TURN_START, List.of(binding("remove-result-only", 0))),
                        Set.of(),
                        Set.of());

        assertInstanceOf(AgentRunOutcome.Failed.class, create(failure, "新问题").run());
        assertEquals(
                2,
                failure.conversation.stream()
                        .filter(message -> message.entryId().startsWith("historical-"))
                        .count());
    }

    private CoreTestFixture fixtureWithHistoricalToolGroup() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.modelResponses.add(new ModelResponse("初始化", List.of(), Map.of()));
        assertInstanceOf(AgentRunOutcome.Completed.class, create(fixture, "初始化").run());
        fixture.conversation.clear();
        fixture.modelRequests.clear();
        fixture.modelCalls = 0;
        fixture.conversation.add(
                new AgentMessageEntry(
                        "historical-call",
                        "session-1",
                        1,
                        0,
                        MessageRole.ASSISTANT,
                        MessageType.TOOL_CALLS,
                        "",
                        Map.of(
                                "toolCalls",
                                List.of(Map.of("toolCallId", "old-call", "name", "old-tool"))),
                        Instant.EPOCH));
        fixture.conversation.add(
                new AgentMessageEntry(
                        "historical-result",
                        "session-1",
                        1,
                        1,
                        MessageRole.TOOL,
                        MessageType.TOOL_RESULT,
                        "旧结果",
                        Map.of("toolCallId", "old-call", "toolName", "old-tool"),
                        Instant.EPOCH));
        return fixture;
    }

    private ApexAgent create(CoreTestFixture fixture, String query) {
        return new ApexAgentFactory()
                .createNew(new AgentRequest("session-1", "demo", "user-1", query), fixture.ports());
    }

    private HookBinding binding(String name, int order) {
        return new HookBinding(name, name, order, true, List.of(), Map.of());
    }

    private HookMutations mutations(MessageOperation... operations) {
        return new HookMutations(List.of(operations), ToolActivationDelta.none());
    }

    private LifecycleHook<PreModelCallContext, PreModelCallHookResult> preModelHook(
            java.util.function.Function<PreModelCallContext, PreModelCallHookResult> action) {
        return hook(
                "pre-model",
                HookPoint.PRE_MODEL_CALL,
                PreModelCallContext.class,
                PreModelCallHookResult.class,
                action);
    }

    private LifecycleHook<PostModelCallContext, PostModelCallHookResult> postModelHook(
            java.util.function.Function<PostModelCallContext, PostModelCallHookResult> action) {
        return hook(
                "post-model",
                HookPoint.POST_MODEL_CALL,
                PostModelCallContext.class,
                PostModelCallHookResult.class,
                action);
    }

    private LifecycleHook<PreMessageCompressionContext, PreMessageCompressionHookResult>
            preCompressionHook(
                    java.util.function.Function<
                                    PreMessageCompressionContext, PreMessageCompressionHookResult>
                            action) {
        return hook(
                "pre-compression",
                HookPoint.PRE_MESSAGE_COMPRESSION,
                PreMessageCompressionContext.class,
                PreMessageCompressionHookResult.class,
                action);
    }

    private LifecycleHook<TurnStartContext, LoopHookResult> turnStartHook(
            java.util.function.Function<TurnStartContext, LoopHookResult> action) {
        return hook(
                "turn-start",
                HookPoint.TURN_START,
                TurnStartContext.class,
                LoopHookResult.class,
                action);
    }

    private <
                    C extends org.gemo.apex.common.hook.context.HookContextView,
                    R extends LifecycleHookResult>
            LifecycleHook<C, R> hook(
                    String name,
                    HookPoint point,
                    Class<C> contextType,
                    Class<R> resultType,
                    java.util.function.Function<C, R> action) {
        return new LifecycleHook<>() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public HookTypeDescriptor descriptor() {
                return new HookTypeDescriptor(point, contextType, resultType);
            }

            @Override
            public R apply(C context) {
                return action.apply(context);
            }
        };
    }
}
