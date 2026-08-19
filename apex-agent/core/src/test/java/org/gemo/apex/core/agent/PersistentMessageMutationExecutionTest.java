package org.gemo.apex.core.agent;

import static org.junit.jupiter.api.Assertions.*;

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
import org.gemo.apex.common.hook.context.PreToolCallContext;
import org.gemo.apex.common.hook.operation.*;
import org.gemo.apex.common.hook.result.*;
import org.gemo.apex.common.message.AgentMessageEntry;
import org.gemo.apex.common.message.MessageRole;
import org.gemo.apex.common.message.MessageType;
import org.gemo.apex.common.model.ModelResponse;
import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.common.tool.ToolResult;
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
    void allowsHookToEditCurrentToolGroupWithoutCoreIntegrityValidation() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.tool(
                "tool",
                (call, context, observer) ->
                        new ToolResult(call.toolCallId(), call.name(), "工具结果", Map.of()));
        fixture.hooks.put(
                "remove-current-call",
                preToolHook(
                        context -> {
                            AgentMessageEntry currentCall =
                                    fixture.conversation.stream()
                                            .filter(
                                                    message ->
                                                            message.messageType()
                                                                    == MessageType.TOOL_CALLS)
                                            .findFirst()
                                            .orElseThrow();
                            return new ContinuePreToolCall(
                                    mutations(
                                            new RemoveMessage(
                                                    "remove-current-call",
                                                    currentCall.entryId())),
                                    new ToolCallPatch(context.toolCall().arguments()));
                        }));
        fixture.definition =
                fixture.definition(
                        Map.of(
                                HookPoint.PRE_TOOL_CALL,
                                List.of(binding("remove-current-call", 0))),
                        Set.of("tool"),
                        Set.of("tool"));
        fixture.modelResponses.add(
                new ModelResponse(
                        "",
                        List.of(new ToolCall("call-1", "tool", 0, Map.of(), Map.of())),
                        Map.of()));
        fixture.modelResponses.add(new ModelResponse("完成", List.of(), Map.of()));

        AgentRunOutcome outcome = create(fixture, "执行工具").run();

        assertInstanceOf(AgentRunOutcome.Completed.class, outcome);
        assertTrue(
                fixture.conversation.stream()
                        .noneMatch(message -> message.messageType() == MessageType.TOOL_CALLS));
        assertTrue(
                fixture.conversation.stream()
                        .anyMatch(message -> message.messageType() == MessageType.TOOL_RESULT));
        assertEquals(
                List.of(MessageType.TEXT, MessageType.TOOL_RESULT),
                fixture.modelRequests.getLast().messages().stream()
                        .map(AgentMessageEntry::messageType)
                        .toList());
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

    private LifecycleHook<PreToolCallContext, PreToolCallHookResult> preToolHook(
            java.util.function.Function<PreToolCallContext, PreToolCallHookResult> action) {
        return hook(
                "pre-tool",
                HookPoint.PRE_TOOL_CALL,
                PreToolCallContext.class,
                PreToolCallHookResult.class,
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
