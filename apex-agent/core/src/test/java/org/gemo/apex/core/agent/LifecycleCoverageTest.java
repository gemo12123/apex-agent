package org.gemo.apex.core.agent;

import org.gemo.apex.common.agent.AgentDefinitionSnapshot;
import org.gemo.apex.common.execution.AgentRequest;
import org.gemo.apex.common.hook.*;
import org.gemo.apex.common.hook.context.*;
import org.gemo.apex.common.hook.operation.*;
import org.gemo.apex.common.hook.result.*;
import org.gemo.apex.common.intervention.QuestionSubmission;
import org.gemo.apex.common.model.ModelResponse;
import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.common.tool.ToolResult;
import org.gemo.apex.core.event.AgentEventEmitter;
import org.gemo.apex.core.event.AgentEventFactory;
import org.gemo.apex.core.lifecycle.LifecycleDispatcher;
import org.gemo.apex.core.tool.ToolCallCoordinator;
import org.gemo.apex.core.tool.ToolCatalog;
import org.gemo.apex.core.tool.ToolResultFactory;
import org.gemo.apex.extension.hook.LifecycleHook;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class LifecycleCoverageTest {
    /**
     * 十一个生命周期均由同一调度器按定义触发
     */
    @Test
    void dispatchesAllElevenLifecycleStagesByDefinitionThroughSameDispatcher() {
        CoreTestFixture fixture = new CoreTestFixture();
        Map<HookPoint, Integer> counts = new EnumMap<>(HookPoint.class);
        Map<HookPoint, List<HookBinding>> bindings = new EnumMap<>(HookPoint.class);
        for (HookPoint point : HookPoint.values()) {
            String name = "hook-" + point;
            fixture.hooks.put(name, hook(point, counts));
            bindings.put(point, List.of(new HookBinding("id-" + point, name, 0,
                    true, List.of(), Map.of())));
        }
        fixture.tool("tool", (call, context, observer) ->
                new ToolResult(call.toolCallId(), call.name(), "ok", Map.of()));
        fixture.definition = fixture.definition(bindings, Set.of("tool"), Set.of("tool"));
        fixture.compact = true;
        fixture.modelResponses.add(new ModelResponse("", List.of(
                new ToolCall("call-1", "tool", 0, Map.of(), Map.of())), Map.of()));
        fixture.modelResponses.add(new ModelResponse("完成", List.of(), Map.of()));

        AgentRunOutcome outcome = new ApexAgentFactory().createNew(
                new AgentRequest("session-1", "demo", "user-1", "你好"), fixture.ports()).run();

        assertInstanceOf(AgentRunOutcome.Completed.class, outcome, outcome.toString());
        assertEquals(1, counts.get(HookPoint.AGENT_BUILD));
        assertEquals(1, counts.get(HookPoint.TURN_START));
        assertEquals(2, counts.get(HookPoint.ITERATION_START));
        assertEquals(2, counts.get(HookPoint.PRE_MESSAGE_COMPRESSION));
        assertEquals(2, counts.get(HookPoint.POST_MESSAGE_COMPRESSION));
        assertEquals(2, counts.get(HookPoint.PRE_MODEL_CALL));
        assertEquals(2, counts.get(HookPoint.POST_MODEL_CALL));
        assertEquals(1, counts.get(HookPoint.PRE_TOOL_CALL));
        assertEquals(1, counts.get(HookPoint.POST_TOOL_CALL));
        assertEquals(2, counts.get(HookPoint.ITERATION_END));
        assertEquals(1, counts.get(HookPoint.TURN_END));
    }

    /**
     * Hook普通异常只跳过当前Binding并继续后续Hook
     */
    @Test
    void ordinaryHookExceptionSkipsOnlyCurrentBindingAndContinuesSubsequentHooks() {
        CoreTestFixture fixture = new CoreTestFixture();
        List<String> invoked = new ArrayList<>();
        fixture.hooks.put("broken", loopHook(context -> {
            invoked.add("broken"); throw new IllegalStateException("boom");
        }));
        fixture.hooks.put("next", loopHook(context -> {
            invoked.add("next"); return new ContinueLoop(HookMutations.none());
        }));
        fixture.definition = fixture.definition(Map.of(HookPoint.TURN_START, List.of(
                new HookBinding("b", "broken", 0, true, List.of(), Map.of()),
                new HookBinding("a", "next", 1, true, List.of(), Map.of()))), Set.of(), Set.of());
        fixture.modelResponses.add(new ModelResponse("完成", List.of(), Map.of()));

        AgentRunOutcome outcome = new ApexAgentFactory().createNew(
                new AgentRequest("session-1", "demo", "user-1", "你好"), fixture.ports()).run();

        assertInstanceOf(AgentRunOutcome.Completed.class, outcome, outcome.toString());
        assertEquals(List.of("broken", "next"), invoked);
    }

    /**
     * Hook非法工具变更不会留下部分状态
     */
    @Test
    void illegalHookToolChangesDoNotLeavePartialState() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.hooks.put("illegal", loopHook(context -> new ContinueLoop(new HookMutations(List.of(),
                new ToolActivationDelta(Set.of("unknown"), Set.of())))));
        fixture.definition = fixture.definition(Map.of(HookPoint.TURN_START, List.of(
                new HookBinding("illegal", "illegal", 0, true, List.of(), Map.of()))), Set.of(), Set.of());
        fixture.modelResponses.add(new ModelResponse("不应调用", List.of(), Map.of()));
        ApexAgent agent = new ApexAgentFactory().createNew(
                new AgentRequest("session-1", "demo", "user-1", "你好"), fixture.ports());

        AgentRunOutcome outcome = agent.run();

        assertInstanceOf(AgentRunOutcome.Failed.class, outcome);
        assertTrue(agent.snapshot().enabledTools().isEmpty());
        assertEquals(0, fixture.modelCalls);
    }

    /**
     * 工具Binding应支持精确与星号匹配并向Hook暴露自身配置
     */
    @Test
    void toolBindingSupportsExactAndWildcardMatchingAndExposesItsConfigurationToHook() {
        CoreTestFixture fixture = new CoreTestFixture();
        List<String> invoked = new ArrayList<>();
        fixture.hooks.put("capture", preToolHook(context -> {
            invoked.add(context.binding().id() + ":" + context.binding().options().get("label"));
            return new ContinuePreToolCall(HookMutations.none(),
                    new ToolCallPatch(context.toolCall().arguments()));
        }));
        fixture.tool("search_web", (call, context, observer) ->
                new ToolResult(call.toolCallId(), call.name(), "ok", Map.of()));
        fixture.tool("lookup", (call, context, observer) ->
                new ToolResult(call.toolCallId(), call.name(), "unused", Map.of()));
        fixture.definition = fixture.definition(Map.of(HookPoint.PRE_TOOL_CALL, List.of(
                new HookBinding("glob", "capture", 0, true, List.of("search_*"), Map.of("label", "glob")),
                new HookBinding("exact", "capture", 1, true, List.of("search_web"), Map.of("label", "exact")),
                new HookBinding("other", "capture", 2, true, List.of("lookup"), Map.of("label", "other")),
                new HookBinding("disabled", "capture", 3, false, List.of("*"), Map.of("label", "disabled")))),
                Set.of("search_web", "lookup"), Set.of("search_web"));
        fixture.modelResponses.add(new ModelResponse("", List.of(
                new ToolCall("call-1", "search_web", 0, Map.of(), Map.of())), Map.of()));
        fixture.modelResponses.add(new ModelResponse("完成", List.of(), Map.of()));

        AgentRunOutcome outcome = new ApexAgentFactory().createNew(
                new AgentRequest("session-1", "demo", "user-1", "你好"), fixture.ports()).run();

        assertInstanceOf(AgentRunOutcome.Completed.class, outcome, outcome.toString());
        assertEquals(List.of("glob:glob", "exact:exact"), invoked);
    }

    /**
     * 人工提交应同时传给PreToolHook和真实工具
     */
    @Test
    void passesHumanSubmissionToBothPreToolHookAndActualTool() {
        CoreTestFixture fixture = new CoreTestFixture();
        QuestionSubmission submission = new QuestionSubmission("call-1", Map.of("0", "答案"));
        List<Object> observed = new ArrayList<>();
        fixture.hooks.put("ask-hook", preToolHook(context -> {
            observed.add(context.humanSubmission());
            return new ContinuePreToolCall(HookMutations.none(),
                    new ToolCallPatch(context.toolCall().arguments()));
        }));
        fixture.tool("ask_human", (call, context, observer) -> {
            observed.add(context.humanSubmission());
            return new ToolResult(call.toolCallId(), call.name(), "ok", Map.of());
        });
        fixture.definition = fixture.definition(Map.of(HookPoint.PRE_TOOL_CALL, List.of(
                new HookBinding("ask", "ask-hook", 0, true, List.of("ask_*"), Map.of()))),
                Set.of("ask_human"), Set.of("ask_human"));
        AgentPorts ports = fixture.ports();
        ApexAgent fresh = new ApexAgentFactory().createNew(
                new AgentRequest("session-1", "demo", "user-1", "你好"), ports);
        ApexAgentContext context = new ApexAgentContext(ports, new AgentDefinitionSnapshot(fixture.definition),
                new ToolCatalog(List.copyOf(fixture.tools.values())), fresh.snapshot(), submission);
        context.startIteration(1);
        AgentEventFactory events = new AgentEventFactory();
        ToolCallCoordinator coordinator = new ToolCallCoordinator(new LifecycleDispatcher(),
                new ToolResultFactory(), new AgentEventEmitter(ports.eventPublisher(), events), events);

        var outcome = coordinator.process(context, List.of(
                new ToolCall("call-1", "ask_human", 0, Map.of(), Map.of())));

        assertInstanceOf(ToolCallCoordinator.ToolCallsOutcome.Completed.class, outcome);
        assertEquals(List.of(submission, submission), observed);
    }

    private LifecycleHook<TurnStartContext, LoopHookResult> loopHook(
            java.util.function.Function<TurnStartContext, LoopHookResult> function) {
        return new LifecycleHook<>() {
            @Override public HookTypeDescriptor descriptor() {
                return new HookTypeDescriptor(HookPoint.TURN_START, TurnStartContext.class,
                        LoopHookResult.class);
            }
            @Override public LoopHookResult apply(TurnStartContext context) { return function.apply(context); }
        };
    }

    private LifecycleHook<PreToolCallContext, PreToolCallHookResult> preToolHook(
            java.util.function.Function<PreToolCallContext, PreToolCallHookResult> function) {
        return new LifecycleHook<>() {
            @Override public HookTypeDescriptor descriptor() {
                return new HookTypeDescriptor(HookPoint.PRE_TOOL_CALL, PreToolCallContext.class,
                        PreToolCallHookResult.class);
            }
            @Override public PreToolCallHookResult apply(PreToolCallContext context) {
                return function.apply(context);
            }
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private LifecycleHook<?, ?> hook(HookPoint point, Map<HookPoint, Integer> counts) {
        Class contextType = contextType(point);
        Class resultType = resultType(point);
        return new LifecycleHook() {
            @Override public HookTypeDescriptor descriptor() {
                return new HookTypeDescriptor(point, contextType, resultType);
            }
            @Override public LifecycleHookResult apply(HookContextView context) {
                counts.merge(point, 1, Integer::sum);
                return result(point, context);
            }
        };
    }

    private LifecycleHookResult result(HookPoint point, HookContextView context) {
        return switch (point) {
            case AGENT_BUILD -> new ContinueAgentBuild(List.of());
            case TURN_START, ITERATION_START, ITERATION_END -> new ContinueLoop(HookMutations.none());
            case PRE_MESSAGE_COMPRESSION -> {
                var value = (PreMessageCompressionContext) context;
                yield new ContinuePreMessageCompression(HookMutations.none(),
                        new ConversationCompactionRequestPatch(value.request()));
            }
            case POST_MESSAGE_COMPRESSION -> {
                var value = (PostMessageCompressionContext) context;
                yield new ContinuePostMessageCompression(HookMutations.none(),
                        new ConversationCompactionResultPatch(value.result()));
            }
            case PRE_MODEL_CALL -> {
                var value = (PreModelCallContext) context;
                yield new ContinuePreModelCall(HookMutations.none(), new ModelRequestPatch(value.request()));
            }
            case POST_MODEL_CALL -> {
                var value = (PostModelCallContext) context;
                yield new ContinuePostModelCall(HookMutations.none(), new ModelResponsePatch(value.response()));
            }
            case PRE_TOOL_CALL -> {
                var value = (PreToolCallContext) context;
                yield new ContinuePreToolCall(HookMutations.none(), new ToolCallPatch(value.toolCall().arguments()));
            }
            case POST_TOOL_CALL -> {
                var value = (PostToolCallContext) context;
                yield new ContinuePostToolCall(HookMutations.none(),
                        new ToolResultPatch(value.toolResult().content(), value.toolResult().metadata()));
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
