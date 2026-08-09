package org.gemo.apex.core.agent;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.gemo.apex.common.agent.*;
import org.gemo.apex.common.execution.AgentRequest;
import org.gemo.apex.common.execution.IterationStatus;
import org.gemo.apex.common.execution.SessionStatus;
import org.gemo.apex.common.execution.TurnStatus;
import org.gemo.apex.common.hook.*;
import org.gemo.apex.common.hook.context.PostToolCallContext;
import org.gemo.apex.common.hook.context.PreToolCallContext;
import org.gemo.apex.common.hook.operation.HookMutations;
import org.gemo.apex.common.hook.operation.SkillActivationDelta;
import org.gemo.apex.common.hook.operation.ToolActivationDelta;
import org.gemo.apex.common.hook.operation.ToolCallPatch;
import org.gemo.apex.common.hook.operation.ToolResultPatch;
import org.gemo.apex.common.hook.result.ContinuePostToolCall;
import org.gemo.apex.common.hook.result.ContinuePreToolCall;
import org.gemo.apex.common.hook.result.PostToolCallHookResult;
import org.gemo.apex.common.hook.result.PreToolCallHookResult;
import org.gemo.apex.common.message.MessageType;
import org.gemo.apex.common.model.ModelResponse;
import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.common.tool.ToolResult;
import org.gemo.apex.extension.hook.LifecycleHook;
import org.gemo.apex.protocol.event.EndMessage;
import org.junit.jupiter.api.Test;

class ApexAgentExecutionTest {
    /** 无工具响应完成单次ReAct并只请求一次End */
    @Test
    void completesSingleReactWithToolFreeResponseAndRequestsEndOnce() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.modelResponses.add(new ModelResponse("完成", List.of(), Map.of()));
        ApexAgent agent = create(fixture);

        AgentRunOutcome outcome = agent.run();

        assertInstanceOf(AgentRunOutcome.Completed.class, outcome, outcome.toString());
        assertEquals(SessionStatus.COMPLETED, agent.snapshot().status());
        assertEquals(1, fixture.modelCalls);
        assertEquals(1, fixture.events.stream().filter(EndMessage.class::isInstance).count());
    }

    /** 多ToolCall按序执行并在工具异常后继续下一轮模型 */
    @Test
    void executesMultipleToolCallsInOrderAndContinuesNextModelRoundAfterToolException() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.tool(
                "ok",
                (call, context, observer) ->
                        new ToolResult(call.toolCallId(), call.name(), "成功", Map.of()));
        fixture.tool(
                "bad",
                (call, context, observer) -> {
                    throw new IllegalStateException("boom");
                });
        fixture.definition = fixture.definition(Map.of(), Set.of("ok", "bad"), Set.of("ok", "bad"));
        fixture.modelResponses.add(
                new ModelResponse(
                        "",
                        List.of(
                                new ToolCall("c1", "ok", 0, Map.of(), Map.of()),
                                new ToolCall("c2", "bad", 1, Map.of(), Map.of())),
                        Map.of()));
        fixture.modelResponses.add(new ModelResponse("最终", List.of(), Map.of()));
        ApexAgent agent = create(fixture);

        AgentRunOutcome outcome = agent.run();

        assertInstanceOf(AgentRunOutcome.Completed.class, outcome, outcome.toString());
        assertEquals(2, fixture.toolCalls);
        List<String> toolContents =
                fixture.conversation.stream()
                        .filter(entry -> entry.messageType() == MessageType.TOOL_RESULT)
                        .map(entry -> entry.content())
                        .toList();
        assertEquals(List.of("成功", "工具执行失败：IllegalStateException"), toolContents);
        assertEquals(2, fixture.modelCalls);
    }

    /** 最后一轮仍返回工具时不执行工具并补齐固定结果 */
    @Test
    void doesNotExecuteToolsAndFillsFixedResultsWhenFinalRoundStillReturnsTools() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.tool(
                "tool",
                (call, context, observer) ->
                        new ToolResult(call.toolCallId(), call.name(), "真实结果", Map.of()));
        fixture.definition = fixture.definition(Map.of(), Set.of("tool"), Set.of("tool"));
        for (int i = 0; i < 3; i++) {
            fixture.modelResponses.add(
                    new ModelResponse(
                            "",
                            List.of(new ToolCall("c" + i, "tool", 0, Map.of(), Map.of())),
                            Map.of()));
        }
        ApexAgent agent = create(fixture);

        AgentRunOutcome outcome = agent.run();

        assertInstanceOf(AgentRunOutcome.EndedByHook.class, outcome, outcome.toString());
        assertEquals(3, fixture.modelCalls);
        assertEquals(2, fixture.toolCalls);
        assertTrue(fixture.modelRequests.getLast().systemPrompt().contains("直接输出最终结论且不再调用工具"));
        assertEquals(
                "达到最大轮次，强制结束",
                fixture.conversation.stream()
                        .filter(entry -> entry.messageType() == MessageType.TOOL_RESULT)
                        .toList()
                        .getLast()
                        .content());
    }

    /** 工具期间取消会停止剩余工具并批量补齐取消结果 */
    @Test
    void stopsRemainingToolsAndFillsCancellationResultsWhenCancelledDuringToolExecution() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.tool(
                "cancel",
                (call, context, observer) -> {
                    fixture.token.cancel();
                    context.cancellationToken().throwIfCancellationRequested();
                    throw new AssertionError();
                });
        fixture.definition = fixture.definition(Map.of(), Set.of("cancel"), Set.of("cancel"));
        fixture.modelResponses.add(
                new ModelResponse(
                        "",
                        List.of(
                                new ToolCall("c1", "cancel", 0, Map.of(), Map.of()),
                                new ToolCall("c2", "cancel", 1, Map.of(), Map.of())),
                        Map.of()));
        ApexAgent agent = create(fixture);

        AgentRunOutcome outcome = agent.run();

        assertInstanceOf(AgentRunOutcome.Cancelled.class, outcome, outcome.toString());
        assertEquals(SessionStatus.CANCELLED, agent.snapshot().status());
        assertEquals(1, fixture.toolCalls);
        assertEquals(
                List.of("请求已取消，工具未执行完成", "请求已取消，工具未执行完成"),
                fixture.conversation.stream()
                        .filter(entry -> entry.messageType() == MessageType.TOOL_RESULT)
                        .map(entry -> entry.content())
                        .toList());
    }

    /** 压缩门按compact再session保存后才调用模型 */
    @Test
    void callsModelOnlyAfterCompactionGateAndSessionSave() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.compact = true;
        fixture.modelResponses.add(new ModelResponse("完成", List.of(), Map.of()));
        ApexAgent agent = create(fixture);

        agent.run();

        int compact = fixture.calls.indexOf("conversation.compact");
        int model = fixture.calls.indexOf("model");
        assertTrue(compact >= 0 && compact < model);
        assertEquals(1, fixture.calls.stream().filter("compact.check"::equals).count());
        assertEquals(1, fixture.calls.stream().filter("compact.execute"::equals).count());
        assertEquals(
                MessageType.SUMMARY,
                fixture.modelRequests.getFirst().messages().getFirst().messageType());
        assertEquals("摘要", fixture.modelRequests.getFirst().messages().getFirst().content());
        assertFalse(fixture.modelRequests.getFirst().systemPrompt().contains("摘要"));
    }

    /** 关闭压缩时跳过策略Hook执行和持久化 */
    @Test
    void skipsAllCompactionBehaviorWhenDisabled() {
        CoreTestFixture fixture = new CoreTestFixture();
        AgentDefinition source = fixture.definition;
        fixture.definition =
                new AgentDefinition(
                        source.schemaVersion(),
                        source.metadata(),
                        source.prompt(),
                        new MessageCompressionDefinition(false, 1, 1L, 1L),
                        source.tools(),
                        source.enabledSkills(),
                        source.subAgents(),
                        source.hooks());
        fixture.compact = true;
        fixture.modelResponses.add(new ModelResponse("完成", List.of(), Map.of()));

        create(fixture).run();

        assertFalse(fixture.calls.contains("compact.check"));
        assertFalse(fixture.calls.contains("compact.execute"));
        assertFalse(fixture.calls.contains("conversation.compact"));
    }

    /** ReAct边界使用Agent定义中的最大轮次 */
    @Test
    void usesMaxIterationsFromFinalAgentDefinition() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.tool(
                "tool",
                (call, context, observer) ->
                        new ToolResult(call.toolCallId(), call.name(), "不应执行", Map.of()));
        AgentDefinition source = fixture.definition(Map.of(), Set.of("tool"), Set.of("tool"));
        fixture.definition =
                new AgentDefinition(
                        source.schemaVersion(),
                        source.metadata(),
                        new PromptDefinition(source.prompt().systemPrompt(), 1),
                        source.messageCompression(),
                        source.tools(),
                        source.enabledSkills(),
                        source.subAgents(),
                        source.hooks());
        fixture.modelResponses.add(
                new ModelResponse(
                        "", List.of(new ToolCall("c1", "tool", 0, Map.of(), Map.of())), Map.of()));

        AgentRunOutcome outcome = create(fixture).run();

        assertInstanceOf(AgentRunOutcome.EndedByHook.class, outcome);
        assertEquals(1, fixture.modelCalls);
        assertEquals(0, fixture.toolCalls);
        assertTrue(fixture.modelRequests.getFirst().systemPrompt().contains("直接输出最终结论"));
    }

    /** 模型失败后三层失败且不执行结束生命周期 */
    @Test
    void failsAtThreeLevelsAndSkipsEndLifecycleAfterModelFailure() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.modelFailure = new IllegalStateException("model down");
        ApexAgent agent = create(fixture);

        AgentRunOutcome outcome = agent.run();

        assertInstanceOf(AgentRunOutcome.Failed.class, outcome);
        assertEquals(SessionStatus.FAILED, agent.snapshot().status());
        assertEquals(TurnStatus.FAILED, agent.snapshot().activeTurn().status());
        assertEquals(
                IterationStatus.FAILED, agent.snapshot().activeTurn().currentIteration().status());
    }

    /** prepared取消不创建Iteration或调用模型工具Hook */
    @Test
    void preparedCancellationDoesNotCreateIterationOrInvokeModelToolOrHook() {
        CoreTestFixture fixture = new CoreTestFixture();
        ApexAgent agent = create(fixture);

        AgentRunOutcome outcome = agent.cancelBeforeRun();

        assertInstanceOf(AgentRunOutcome.Cancelled.class, outcome);
        assertEquals(SessionStatus.CANCELLED, agent.snapshot().status());
        assertNull(agent.snapshot().activeTurn().currentIteration());
        assertEquals(0, fixture.modelCalls);
        assertEquals(0, fixture.toolCalls);
    }

    /** Hook中途禁用会阻止同一响应中的后续伪造调用 */
    @Test
    void midHookToolDisablementPreventsSubsequentForgedCallsInSameResponse() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.tool(
                "first",
                (call, context, observer) ->
                        new ToolResult(call.toolCallId(), call.name(), "first-ok", Map.of()));
        fixture.tool(
                "second",
                (call, context, observer) ->
                        new ToolResult(call.toolCallId(), call.name(), "second-ok", Map.of()));
        fixture.hooks.put(
                "disable-second",
                new LifecycleHook<PreToolCallContext, PreToolCallHookResult>() {
                    @Override
                    public HookTypeDescriptor descriptor() {
                        return new HookTypeDescriptor(
                                HookPoint.PRE_TOOL_CALL,
                                PreToolCallContext.class,
                                PreToolCallHookResult.class);
                    }

                    @Override
                    public PreToolCallHookResult apply(PreToolCallContext context) {
                        return new ContinuePreToolCall(
                                new HookMutations(
                                        List.of(),
                                        new ToolActivationDelta(Set.of(), Set.of("second"))),
                                new ToolCallPatch(context.toolCall().arguments()));
                    }
                });
        fixture.definition =
                fixture.definition(
                        Map.of(
                                HookPoint.PRE_TOOL_CALL,
                                List.of(
                                        new HookBinding(
                                                "disable",
                                                "disable-second",
                                                0,
                                                true,
                                                List.of("first"),
                                                Map.of()))),
                        Set.of("first", "second"),
                        Set.of("first", "second"));
        fixture.modelResponses.add(
                new ModelResponse(
                        "",
                        List.of(
                                new ToolCall("c1", "first", 0, Map.of(), Map.of()),
                                new ToolCall("c2", "second", 1, Map.of(), Map.of())),
                        Map.of()));
        fixture.modelResponses.add(new ModelResponse("完成", List.of(), Map.of()));

        AgentRunOutcome outcome = create(fixture).run();

        assertInstanceOf(AgentRunOutcome.Completed.class, outcome, outcome.toString());
        assertEquals(1, fixture.toolCalls);
        assertEquals(
                "工具当前未启用，无法执行",
                fixture.conversation.stream()
                        .filter(entry -> entry.messageType() == MessageType.TOOL_RESULT)
                        .toList()
                        .getLast()
                        .content());
    }

    /** 工具发布非白名单事件会转换为当前工具失败结果 */
    @Test
    void convertsToolPublishedNonAllowlistedEventToCurrentToolFailureResult() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.tool(
                "bad-event",
                (call, context, observer) -> {
                    observer.onEvent(EndMessage.builder().build());
                    return new ToolResult(call.toolCallId(), call.name(), "不应到达", Map.of());
                });
        fixture.definition = fixture.definition(Map.of(), Set.of("bad-event"), Set.of("bad-event"));
        fixture.modelResponses.add(
                new ModelResponse(
                        "",
                        List.of(new ToolCall("c1", "bad-event", 0, Map.of(), Map.of())),
                        Map.of()));
        fixture.modelResponses.add(new ModelResponse("完成", List.of(), Map.of()));

        AgentRunOutcome outcome = create(fixture).run();

        assertInstanceOf(AgentRunOutcome.Completed.class, outcome, outcome.toString());
        assertEquals(
                "工具执行失败：IllegalToolEventException",
                fixture.conversation.stream()
                        .filter(entry -> entry.messageType() == MessageType.TOOL_RESULT)
                        .findFirst()
                        .orElseThrow()
                        .content());
    }

    /** activate_skill按普通工具执行且Hook激活状态跨Turn幂等保留 */
    @Test
    void runsActivateSkillAsOrdinaryToolAndPersistsHookStateAcrossTurns() {
        CoreTestFixture fixture = new CoreTestFixture();
        List<Set<String>> activationInputs = new java.util.ArrayList<>();
        fixture.tool(
                "activate_skill",
                (call, context, observer) -> {
                    activationInputs.add(context.activatedSkills());
                    assertEquals(Set.of("pdf"), context.enabledSkills());
                    return new ToolResult(
                            call.toolCallId(), call.name(), "instructions:pdf", Map.of());
                });
        fixture.hooks.put("activate-state", activationStateHook());
        fixture.definition =
                fixture.definition(
                        activationBindings("activate_skill"),
                        Set.of("activate_skill"),
                        Set.of("activate_skill"),
                        Set.of("pdf"));
        fixture.modelResponses.add(activationResponse("activate-1"));
        fixture.modelResponses.add(new ModelResponse("第一轮完成", List.of(), Map.of()));

        ApexAgent first = create(fixture);
        assertInstanceOf(AgentRunOutcome.Completed.class, first.run());

        assertEquals(Set.of("pdf"), first.snapshot().activatedSkills());
        assertEquals(List.of(Set.of()), activationInputs);
        assertEquals(
                "instructions:pdf",
                fixture.conversation.stream()
                        .filter(entry -> entry.messageType() == MessageType.TOOL_RESULT)
                        .findFirst()
                        .orElseThrow()
                        .content());
        assertEquals(1, fixture.toolCalls);

        fixture.modelResponses.add(activationResponse("activate-2"));
        fixture.modelResponses.add(new ModelResponse("第二轮完成", List.of(), Map.of()));
        ApexAgent second =
                new ApexAgentFactory()
                        .createNew(
                                new AgentRequest("session-1", "demo", "user-1", "继续"),
                                fixture.ports());
        assertInstanceOf(AgentRunOutcome.Completed.class, second.run());

        assertEquals(Set.of("pdf"), second.snapshot().activatedSkills());
        assertEquals(List.of(Set.of(), Set.of("pdf")), activationInputs);
        assertEquals(2, fixture.toolCalls);
    }

    /** 新Turn按最新定义清理已移除的激活Skill */
    @Test
    void cleansRemovedActivatedSkillsUsingLatestDefinitionForNewTurn() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.tool(
                "custom-activate",
                (call, context, observer) ->
                        new ToolResult(
                                call.toolCallId(), call.name(), "instructions:pdf", Map.of()));
        fixture.hooks.put("activate-state", activationStateHook());
        fixture.definition =
                fixture.definition(
                        activationBindings("custom-activate"),
                        Set.of("custom-activate"),
                        Set.of("custom-activate"),
                        Set.of("pdf"));
        fixture.modelResponses.add(activationResponse("activate-1", "custom-activate"));
        fixture.modelResponses.add(new ModelResponse("完成", List.of(), Map.of()));
        create(fixture).run();
        fixture.definition =
                fixture.definition(
                        Map.of(), Set.of("custom-activate"), Set.of("custom-activate"), Set.of());

        ApexAgent next =
                new ApexAgentFactory()
                        .createNew(
                                new AgentRequest("session-1", "demo", "user-1", "下一轮"),
                                fixture.ports());

        assertTrue(next.snapshot().activatedSkills().isEmpty());
        assertEquals(2, next.snapshot().currentTurnNo());
    }

    /** activateSkill结果追加失败时不持久化激活状态 */
    @Test
    void doesNotPersistActivationStateWhenActivateSkillResultAppendFails() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.tool(
                "activate_skill",
                (call, context, observer) ->
                        new ToolResult(
                                call.toolCallId(), call.name(), "instructions:pdf", Map.of()));
        fixture.hooks.put("activate-state", activationStateHook());
        fixture.definition =
                fixture.definition(
                        activationBindings("activate_skill"),
                        Set.of("activate_skill"),
                        Set.of("activate_skill"),
                        Set.of("pdf"));
        fixture.modelResponses.add(activationResponse("activate-1"));
        ApexAgent agent = create(fixture);
        fixture.failToolResultAppend = true;

        AgentRunOutcome outcome = agent.run();

        assertInstanceOf(AgentRunOutcome.Failed.class, outcome);
        assertTrue(agent.snapshot().activatedSkills().isEmpty());
        assertTrue(fixture.sessions.get("session-1").activatedSkills().isEmpty());
        assertEquals(1, fixture.toolCalls);
    }

    private Map<HookPoint, List<HookBinding>> activationBindings(String toolName) {
        return Map.of(
                HookPoint.POST_TOOL_CALL,
                List.of(
                        new HookBinding(
                                "activate-state-binding",
                                "activate-state",
                                0,
                                true,
                                List.of(toolName),
                                Map.of())));
    }

    private LifecycleHook<PostToolCallContext, PostToolCallHookResult> activationStateHook() {
        return new LifecycleHook<>() {
            @Override
            public HookTypeDescriptor descriptor() {
                return new HookTypeDescriptor(
                        HookPoint.POST_TOOL_CALL,
                        PostToolCallContext.class,
                        PostToolCallHookResult.class);
            }

            @Override
            public PostToolCallHookResult apply(PostToolCallContext context) {
                String skillName = (String) context.toolCall().arguments().get("command");
                return new ContinuePostToolCall(
                        HookMutations.none(),
                        new ToolResultPatch(
                                context.toolResult().content(), context.toolResult().metadata()),
                        new SkillActivationDelta(Set.of(skillName), Set.of()));
            }
        };
    }

    private ModelResponse activationResponse(String callId) {
        return activationResponse(callId, "activate_skill");
    }

    private ModelResponse activationResponse(String callId, String toolName) {
        return new ModelResponse(
                "",
                List.of(new ToolCall(callId, toolName, 0, Map.of("command", "pdf"), Map.of())),
                Map.of());
    }

    private ApexAgent create(CoreTestFixture fixture) {
        return new ApexAgentFactory()
                .createNew(new AgentRequest("session-1", "demo", "user-1", "你好"), fixture.ports());
    }
}
