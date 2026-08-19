package org.gemo.apex.core.agent;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.gemo.apex.common.execution.AgentRequest;
import org.gemo.apex.common.execution.IterationStatus;
import org.gemo.apex.common.execution.SessionStatus;
import org.gemo.apex.common.execution.TurnStatus;
import org.gemo.apex.common.hook.*;
import org.gemo.apex.common.hook.context.PostToolCallContext;
import org.gemo.apex.common.hook.context.PreToolCallContext;
import org.gemo.apex.common.hook.operation.HookMutations;
import org.gemo.apex.common.hook.operation.ToolCallPatch;
import org.gemo.apex.common.hook.operation.ToolResultPatch;
import org.gemo.apex.common.hook.result.*;
import org.gemo.apex.common.intervention.*;
import org.gemo.apex.common.message.AgentMessageEntry;
import org.gemo.apex.common.message.MessageType;
import org.gemo.apex.common.model.ModelResponse;
import org.gemo.apex.common.shared.SharedDataCleanupPolicy;
import org.gemo.apex.common.tool.ToolAvailabilitySnapshot;
import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.common.tool.ToolResult;
import org.gemo.apex.core.exception.InvalidHumanResponseException;
import org.gemo.apex.extension.hook.LifecycleHook;
import org.gemo.apex.protocol.event.EndMessage;
import org.gemo.apex.protocol.event.HumanInterventionMessage;
import org.gemo.apex.protocol.event.InvocationDeclaredMessage;
import org.gemo.apex.protocol.event.detail.AskHumanInterventionDetail;
import org.gemo.apex.protocol.event.detail.ToolConfirmationDetail;
import org.junit.jupiter.api.Test;

class HumanInterventionExecutionTest {
    @Test
    void persistsSharedDataAcrossSuspensionAndResume() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.tool(
                "ask",
                (call, context, observer) -> {
                    assertEquals("resumed", context.sharedData().get("phase"));
                    context.sharedData().put("tool", "done", SharedDataCleanupPolicy.NEVER);
                    return new ToolResult(call.toolCallId(), call.name(), "ok", Map.of());
                });
        Scenario scenario =
                scenario(
                        fixture,
                        context -> {
                            context.sharedData()
                                    .put("phase", "suspended", SharedDataCleanupPolicy.NEVER);
                            return new RequestHumanIntervention(question(context.toolCall()));
                        },
                        context -> {
                            assertEquals("suspended", context.sharedData().get("phase"));
                            context.sharedData()
                                    .put("phase", "resumed", SharedDataCleanupPolicy.NEVER);
                            return continued(context);
                        },
                        false);

        assertInstanceOf(AgentRunOutcome.Suspended.class, scenario.fresh.run());
        assertEquals("suspended", scenario.fresh.snapshot().sharedData().get("phase").value());

        ApexAgent resumed = resumeQuestion(scenario);
        assertInstanceOf(AgentRunOutcome.Completed.class, resumed.run());
        assertEquals("resumed", resumed.snapshot().sharedData().get("phase").value());
        assertEquals("done", resumed.snapshot().sharedData().get("tool").value());
    }

    /** question挂起先保存再发布且不执行工具和结束生命周期 */
    @Test
    void questionSuspensionSavesBeforePublishingAndDoesNotExecuteToolOrEndLifecycle() {
        Scenario scenario = questionScenario(context -> continued(context));

        AgentRunOutcome outcome = scenario.fresh.run();

        assertInstanceOf(AgentRunOutcome.Suspended.class, outcome);
        var snapshot = scenario.fresh.snapshot();
        assertEquals(SessionStatus.HUMAN_IN_THE_LOOP, snapshot.status());
        assertEquals(TurnStatus.SUSPENDED, snapshot.activeTurn().status());
        assertEquals(IterationStatus.SUSPENDED, snapshot.activeTurn().currentIteration().status());
        assertEquals(
                List.of("pre-1"),
                snapshot.suspendedToolBatch().toolCalls().getFirst().executedPreToolHookIds());
        assertEquals(
                "patched",
                snapshot.suspendedToolBatch()
                        .toolCalls()
                        .getFirst()
                        .resolvedArguments()
                        .get("value"));
        assertFalse(
                toolCallPayload(scenario.fixture.conversation, "call-1")
                        .containsKey("resolvedArguments"));
        assertEquals(0, scenario.fixture.toolCalls);
        int saved = scenario.fixture.calls.lastIndexOf("session.save");
        int published = scenario.fixture.calls.indexOf("event.HumanInterventionMessage");
        assertTrue(saved >= 0 && saved < published);
        assertEquals(
                1,
                scenario.fixture.events.stream()
                        .filter(HumanInterventionMessage.class::isInstance)
                        .count());
        assertEquals(
                1, scenario.fixture.events.stream().filter(EndMessage.class::isInstance).count());
        assertEquals(0, scenario.postCalls.get());
    }

    /** 挂起保存失败时不发布交互事件 */
    @Test
    void doesNotPublishInteractionEventWhenSuspensionSaveFails() {
        Scenario scenario = questionScenario(context -> continued(context));
        scenario.fixture.failSuspensionSave = true;

        AgentRunOutcome outcome = scenario.fresh.run();

        assertInstanceOf(AgentRunOutcome.Failed.class, outcome);
        assertEquals(
                0,
                scenario.fixture.events.stream()
                        .filter(HumanInterventionMessage.class::isInstance)
                        .count());
        assertEquals(0, scenario.fixture.toolCalls);
    }

    /** 恢复后再次介入替换唯一挂起对象并累计HookId */
    @Test
    void replacesSingleSuspensionAndAccumulatesHookIdsOnInterventionAfterResume() {
        Scenario scenario =
                questionScenario(
                        context -> new RequestHumanIntervention(question(context.toolCall())));
        assertInstanceOf(AgentRunOutcome.Suspended.class, scenario.fresh.run());

        ApexAgent resumed = resumeQuestion(scenario);
        AgentRunOutcome outcome = resumed.run();

        assertInstanceOf(AgentRunOutcome.Suspended.class, outcome);
        assertEquals(2, scenario.fixture.windowLoads);
        assertEquals(
                List.of("pre-1", "pre-2"),
                resumed.snapshot()
                        .suspendedToolBatch()
                        .toolCalls()
                        .getFirst()
                        .executedPreToolHookIds());
        assertEquals(0, scenario.fixture.toolCalls);
        assertEquals(
                2,
                scenario.fixture.events.stream()
                        .filter(HumanInterventionMessage.class::isInstance)
                        .count());
    }

    /** 恢复END_TURN为当前及剩余ToolCall补齐固定结果 */
    @Test
    void fillsFixedResultsForCurrentAndRemainingToolCallsWhenResumingEndTurn() {
        Scenario scenario = questionScenario(context -> new EndTurnPreToolCall("stop"), true);
        assertInstanceOf(AgentRunOutcome.Suspended.class, scenario.fresh.run());

        AgentRunOutcome outcome = resumeQuestion(scenario).run();

        assertInstanceOf(AgentRunOutcome.EndedByHook.class, outcome);
        assertEquals(List.of("达到最大轮次，强制结束", "达到最大轮次，强制结束"), toolContents(scenario.fixture));
        assertEquals(0, scenario.fixture.toolCalls);
        assertNull(scenario.fixture.sessions.get("session-1").suspendedToolBatch());
        assertTrue(
                toolCallPayload(scenario.fixture.conversation, "call-1")
                        .containsKey("resolvedArguments"));
        assertFalse(
                toolCallPayload(scenario.fixture.conversation, "call-2")
                        .containsKey("resolvedArguments"));
    }

    /** 恢复BLOCK和RETURN均执行POST并清除挂起 */
    @Test
    void runsPostAndClearsSuspensionWhenResumingBlockOrReturn() {
        Scenario blocked = questionScenario(context -> new BlockTool("policy"));
        assertInstanceOf(AgentRunOutcome.Suspended.class, blocked.fresh.run());
        blocked.fixture.modelResponses.add(new ModelResponse("完成", List.of(), Map.of()));
        assertInstanceOf(AgentRunOutcome.Completed.class, resumeQuestion(blocked).run());
        assertEquals("工具执行被阻断：policy", toolContents(blocked.fixture).getFirst());
        assertEquals(1, blocked.postCalls.get());
        assertEquals(
                0,
                blocked.fixture.events.stream()
                        .filter(InvocationDeclaredMessage.class::isInstance)
                        .count());
        assertTrue(
                toolCallPayload(blocked.fixture.conversation, "call-1")
                        .containsKey("resolvedArguments"));

        Scenario returned =
                questionScenario(
                        context ->
                                new ReturnToolResult(
                                        new ToolResult(
                                                context.toolCall().toolCallId(),
                                                context.toolCall().name(),
                                                "直接结果",
                                                Map.of())));
        assertInstanceOf(AgentRunOutcome.Suspended.class, returned.fresh.run());
        returned.fixture.modelResponses.add(new ModelResponse("完成", List.of(), Map.of()));
        assertInstanceOf(AgentRunOutcome.Completed.class, resumeQuestion(returned).run());
        assertEquals("直接结果", toolContents(returned.fixture).getFirst());
        assertEquals(1, returned.postCalls.get());
        assertEquals(
                0,
                returned.fixture.events.stream()
                        .filter(InvocationDeclaredMessage.class::isInstance)
                        .count());
        assertTrue(
                toolCallPayload(returned.fixture.conversation, "call-1")
                        .containsKey("resolvedArguments"));
    }

    /** 恢复全部CONTINUE时askHuman读取typed答案并继续下一Iteration */
    @Test
    void readsTypedAnswersFromAskHumanAndContinuesNextIterationWhenAllResumeResultsContinue() {
        AtomicReference<HumanSubmission> received = new AtomicReference<>();
        Scenario scenario = questionScenario(context -> continued(context));
        scenario.fixture.tools.clear();
        scenario.fixture.tool(
                "ask",
                (call, context, observer) -> {
                    received.set(context.humanSubmission());
                    return new ToolResult(call.toolCallId(), call.name(), "answers", Map.of());
                });
        assertInstanceOf(AgentRunOutcome.Suspended.class, scenario.fresh.run());
        String stableInvocationId =
                scenario.fresh
                        .snapshot()
                        .suspendedToolBatch()
                        .toolCalls()
                        .getFirst()
                        .invocationId();
        assertEquals(
                0,
                scenario.fixture.events.stream()
                        .filter(InvocationDeclaredMessage.class::isInstance)
                        .count());
        scenario.fixture.modelResponses.add(new ModelResponse("完成", List.of(), Map.of()));

        AgentRunOutcome outcome = resumeQuestion(scenario).run();

        assertInstanceOf(AgentRunOutcome.Completed.class, outcome);
        QuestionSubmission submission = assertInstanceOf(QuestionSubmission.class, received.get());
        assertEquals(Map.of("0", "A"), submission.answers());
        assertEquals(1, scenario.fixture.toolCalls);
        assertEquals(1, scenario.postCalls.get());
        assertNull(scenario.fixture.sessions.get("session-1").suspendedToolBatch());
        InvocationDeclaredMessage resumedDeclaration =
                scenario.fixture.events.stream()
                        .filter(InvocationDeclaredMessage.class::isInstance)
                        .map(InvocationDeclaredMessage.class::cast)
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                stableInvocationId, resumedDeclaration.getMessages().getFirst().getInvocationId());
    }

    /** 工具确认批准只合并可编辑参数而拒绝使用唯一固定结果 */
    @Test
    void mergesOnlyEditableParametersAndRejectsFixedResultsWhenToolConfirmationApproved() {
        AtomicReference<Map<String, Object>> arguments = new AtomicReference<>();
        Scenario approved = confirmationScenario(context -> continued(context), arguments);
        assertInstanceOf(AgentRunOutcome.Suspended.class, approved.fresh.run());
        approved.fixture.modelResponses.add(new ModelResponse("完成", List.of(), Map.of()));
        ApexAgent approvedAgent =
                new ApexAgentFactory()
                        .createResumed(
                                command(
                                        Map.of(
                                                "interaction_type",
                                                "TOOL_CONFIRMATION",
                                                "confirmation_id",
                                                confirmationId(approved),
                                                "decision",
                                                "APPROVE",
                                                "updated_args",
                                                Map.of("room", "B", "locked", "changed"))),
                                approved.fixture.ports());
        assertInstanceOf(AgentRunOutcome.Completed.class, approvedAgent.run());
        assertEquals("B", arguments.get().get("room"));
        assertEquals("original", arguments.get().get("locked"));
        Map<?, ?> audited = toolCallPayload(approved.fixture.conversation, "call-1");
        assertEquals("A", ((Map<?, ?>) audited.get("arguments")).get("room"));
        assertEquals("B", ((Map<?, ?>) audited.get("resolvedArguments")).get("room"));
        assertEquals("original", ((Map<?, ?>) audited.get("resolvedArguments")).get("locked"));

        Scenario denied =
                confirmationScenario(context -> continued(context), new AtomicReference<>());
        assertInstanceOf(AgentRunOutcome.Suspended.class, denied.fresh.run());
        ApexAgent deniedAgent =
                new ApexAgentFactory()
                        .createResumed(
                                command(
                                        Map.of(
                                                "interaction_type",
                                                "TOOL_CONFIRMATION",
                                                "confirmation_id",
                                                confirmationId(denied),
                                                "decision",
                                                "DENY")),
                                denied.fixture.ports());
        denied.fixture.modelResponses.add(new ModelResponse("完成", List.of(), Map.of()));
        assertInstanceOf(AgentRunOutcome.Completed.class, deniedAgent.run());
        assertEquals("用户拒绝执行", toolContents(denied.fixture).getFirst());
        assertEquals(0, denied.fixture.toolCalls);
        assertEquals(1, denied.postCalls.get());
        assertEquals(
                0,
                denied.fixture.events.stream()
                        .filter(InvocationDeclaredMessage.class::isInstance)
                        .count());
    }

    /** 非法恢复在解析阶段拒绝且不保存不执行扩展 */
    @Test
    void rejectsInvalidResumeWhenCoreProcessesTheInterventionWithoutExecutingTools() {
        Scenario scenario =
                confirmationScenario(context -> continued(context), new AtomicReference<>());
        assertInstanceOf(AgentRunOutcome.Suspended.class, scenario.fresh.run());
        scenario.fixture.calls.clear();

        ApexAgent resumed =
                new ApexAgentFactory()
                        .createResumed(
                                command(
                                        Map.of(
                                                "interaction_type",
                                                "TOOL_CONFIRMATION",
                                                "confirmation_id",
                                                "wrong",
                                                "decision",
                                                "APPROVE")),
                                scenario.fixture.ports());
        AgentRunOutcome.Failed failed =
                assertInstanceOf(AgentRunOutcome.Failed.class, resumed.run());

        assertInstanceOf(InvalidHumanResponseException.class, failed.cause());
        assertEquals(0, scenario.fixture.toolCalls);
        assertEquals(
                SessionStatus.HUMAN_IN_THE_LOOP,
                scenario.fixture.sessions.get("session-1").status());
        assertNotNull(scenario.fixture.sessions.get("session-1").suspendedToolBatch());
    }

    @Test
    void rejectsResponsesForUnknownToolCallIds() {
        Scenario scenario = questionScenario(context -> continued(context));
        assertInstanceOf(AgentRunOutcome.Suspended.class, scenario.fresh.run());

        ApexAgent resumed =
                new ApexAgentFactory()
                        .createResumed(
                                new HumanResponseCommand(
                                        "session-1",
                                        "demo",
                                        "user-1",
                                        Map.of(
                                                "unknown-call",
                                                Map.of(
                                                        "interaction_type",
                                                        "ASK_HUMAN",
                                                        "answers",
                                                        Map.of()))),
                                scenario.fixture.ports());
        AgentRunOutcome.Failed failed =
                assertInstanceOf(AgentRunOutcome.Failed.class, resumed.run());

        assertInstanceOf(InvalidHumanResponseException.class, failed.cause());
        assertEquals(0, scenario.fixture.toolCalls);
        assertEquals(
                SessionStatus.HUMAN_IN_THE_LOOP,
                scenario.fixture.sessions.get("session-1").status());
    }

    @Test
    void preflightsMixedInterventionsInToolCallOrderAndResumesWithSparseResponses() {
        CoreTestFixture fixture = new CoreTestFixture();
        AtomicInteger preCalls = new AtomicInteger();
        AtomicInteger postCalls = new AtomicInteger();
        fixture.tool(
                "ask",
                (call, context, observer) ->
                        new ToolResult(
                                call.toolCallId(), call.name(), call.toolCallId(), Map.of()));
        fixture.tool(
                "confirm",
                (call, context, observer) ->
                        new ToolResult(
                                call.toolCallId(), call.name(), call.toolCallId(), Map.of()));
        fixture.hooks.put(
                "ask-hook",
                preHook(
                        context -> {
                            preCalls.incrementAndGet();
                            return new RequestHumanIntervention(question(context.toolCall()));
                        }));
        fixture.hooks.put(
                "confirm-hook",
                preHook(
                        context -> {
                            preCalls.incrementAndGet();
                            return new RequestHumanIntervention(confirmation(context));
                        }));
        fixture.hooks.put(
                "post",
                new LifecycleHook<PostToolCallContext, PostToolCallHookResult>() {
                    @Override
                    public String name() {
                        return "post";
                    }

                    @Override
                    public HookTypeDescriptor descriptor() {
                        return new HookTypeDescriptor(
                                HookPoint.POST_TOOL_CALL,
                                PostToolCallContext.class,
                                PostToolCallHookResult.class);
                    }

                    @Override
                    public PostToolCallHookResult apply(PostToolCallContext context) {
                        postCalls.incrementAndGet();
                        return new ContinuePostToolCall(
                                HookMutations.none(),
                                new ToolResultPatch(
                                        context.toolResult().content(),
                                        context.toolResult().metadata()));
                    }
                });
        fixture.definition =
                fixture.definition(
                        Map.of(
                                HookPoint.PRE_TOOL_CALL,
                                        List.of(
                                                new HookBinding(
                                                        "pre-ask",
                                                        "ask-hook",
                                                        0,
                                                        true,
                                                        List.of("ask"),
                                                        Map.of()),
                                                new HookBinding(
                                                        "pre-confirm",
                                                        "confirm-hook",
                                                        1,
                                                        true,
                                                        List.of("confirm"),
                                                        Map.of())),
                                HookPoint.POST_TOOL_CALL,
                                        List.of(
                                                new HookBinding(
                                                        "post", "post", 0, true, List.of(),
                                                        Map.of()))),
                        Set.of("ask", "confirm"),
                        Set.of("ask", "confirm"));
        fixture.modelResponses.add(
                new ModelResponse(
                        "",
                        List.of(
                                new ToolCall("call-1", "ask", 0, Map.of(), Map.of()),
                                new ToolCall(
                                        "call-2", "confirm", 1, Map.of("room", "A"), Map.of())),
                        Map.of()));

        ApexAgent fresh =
                new ApexAgentFactory()
                        .createNew(
                                new AgentRequest("session-1", "demo", "user-1", "问题"),
                                fixture.ports());
        assertInstanceOf(AgentRunOutcome.Suspended.class, fresh.run());

        assertEquals(2, preCalls.get());
        assertEquals(0, fixture.toolCalls);
        assertEquals(0, postCalls.get());
        assertEquals(
                List.of("call-1", "call-2"),
                fresh.snapshot().suspendedToolBatch().toolCalls().stream()
                        .map(item -> item.toolCallId())
                        .toList());
        HumanInterventionMessage event =
                fixture.events.stream()
                        .filter(HumanInterventionMessage.class::isInstance)
                        .map(HumanInterventionMessage.class::cast)
                        .findFirst()
                        .orElseThrow();
        assertInstanceOf(AskHumanInterventionDetail.class, event.getMessages().getFirst());
        assertInstanceOf(ToolConfirmationDetail.class, event.getMessages().get(1));

        fixture.modelResponses.add(new ModelResponse("完成", List.of(), Map.of()));
        ApexAgent resumed =
                new ApexAgentFactory()
                        .createResumed(
                                new HumanResponseCommand(
                                        "session-1",
                                        "demo",
                                        "user-1",
                                        Map.of(
                                                "call-1",
                                                Map.of(
                                                        "interaction_type",
                                                        "ASK_HUMAN",
                                                        "answers",
                                                        Map.of("0", "A")))),
                                fixture.ports());
        assertInstanceOf(AgentRunOutcome.Completed.class, resumed.run());
        assertEquals(List.of("call-1", "call-2"), toolContents(fixture));
        assertEquals(2, fixture.toolCalls);
        assertEquals(2, postCalls.get());
    }

    /** 多ToolCall恢复保留前序结果并继续后序调用 */
    @Test
    void preservesPriorResultsAndContinuesSubsequentCallsWhenResumingMultipleToolCalls() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.tool(
                "plain",
                (call, context, observer) ->
                        new ToolResult(
                                call.toolCallId(), call.name(), call.toolCallId(), Map.of()));
        fixture.tool(
                "ask",
                (call, context, observer) ->
                        new ToolResult(
                                call.toolCallId(), call.name(), call.toolCallId(), Map.of()));
        fixture.hooks.put(
                "first",
                preHook(context -> new RequestHumanIntervention(question(context.toolCall()))));
        fixture.definition =
                fixture.definition(
                        Map.of(
                                HookPoint.PRE_TOOL_CALL,
                                List.of(
                                        new HookBinding(
                                                "pre-1",
                                                "first",
                                                0,
                                                true,
                                                List.of("ask"),
                                                Map.of()))),
                        Set.of("plain", "ask"),
                        Set.of("plain", "ask"));
        fixture.modelResponses.add(
                new ModelResponse(
                        "",
                        List.of(
                                new ToolCall("call-0", "plain", 0, Map.of(), Map.of()),
                                new ToolCall("call-1", "ask", 1, Map.of(), Map.of()),
                                new ToolCall("call-2", "plain", 2, Map.of(), Map.of())),
                        Map.of()));
        ApexAgent fresh =
                new ApexAgentFactory()
                        .createNew(
                                new AgentRequest("session-1", "demo", "user-1", "问题"),
                                fixture.ports());
        assertInstanceOf(AgentRunOutcome.Suspended.class, fresh.run());
        assertEquals(List.of(), toolContents(fixture));
        assertEquals(0, fixture.toolCalls);
        fixture.modelResponses.add(new ModelResponse("完成", List.of(), Map.of()));

        ApexAgent resumed =
                new ApexAgentFactory()
                        .createResumed(
                                command(
                                        Map.of(
                                                "interaction_type",
                                                "ASK_HUMAN",
                                                "answers",
                                                Map.of("0", "A"))),
                                fixture.ports());
        assertInstanceOf(AgentRunOutcome.Completed.class, resumed.run());

        assertEquals(List.of("call-0", "call-1", "call-2"), toolContents(fixture));
        assertEquals(3, fixture.toolCalls);
        assertEquals(1, resumed.snapshot().activeTurn().turnNo());
    }

    /** 挂起工具恢复前转为不可用时迁移历史且不执行工具 */
    @Test
    void migratesSuspendedToolToHistoryWithoutExecutingItWhenItBecomesUnavailableBeforeResume() {
        Scenario scenario = questionScenario(context -> continued(context));
        assertInstanceOf(AgentRunOutcome.Suspended.class, scenario.fresh.run());
        scenario.fixture.tools.clear();
        scenario.fixture.availability = new ToolAvailabilitySnapshot(Set.of("ask"), List.of());
        scenario.fixture.modelResponses.add(new ModelResponse("完成", List.of(), Map.of()));

        ApexAgent resumed =
                new ApexAgentFactory()
                        .createResumed(
                                command(
                                        Map.of(
                                                "interaction_type",
                                                "ASK_HUMAN",
                                                "answers",
                                                Map.of())),
                                scenario.fixture.ports());
        assertInstanceOf(AgentRunOutcome.Completed.class, resumed.run());

        assertEquals("工具不可用", toolContents(scenario.fixture).getFirst());
        assertEquals(0, scenario.fixture.toolCalls);
        assertEquals(
                0,
                scenario.fixture.events.stream()
                        .filter(InvocationDeclaredMessage.class::isInstance)
                        .count());
        assertFalse(resumed.snapshot().enabledTools().contains("ask"));
        assertEquals(
                List.of("ask"),
                resumed.snapshot().historicalToolBindings().stream()
                        .map(binding -> binding.toolName())
                        .toList());
    }

    /** 恢复结果session保存失败时保留持久化挂起并以稳定entryId重试 */
    @Test
    void retainsPersistedSuspensionAndRetriesWithStableEntryIdWhenResumeResultSessionSaveFails() {
        Scenario scenario = questionScenario(context -> continued(context));
        assertInstanceOf(AgentRunOutcome.Suspended.class, scenario.fresh.run());
        scenario.fixture.remainingSessionSaveFailures = 1;
        ApexAgent firstResume = resumeQuestion(scenario);

        assertInstanceOf(AgentRunOutcome.Failed.class, firstResume.run());
        assertEquals(
                SessionStatus.HUMAN_IN_THE_LOOP,
                scenario.fixture.sessions.get("session-1").status());
        assertNotNull(scenario.fixture.sessions.get("session-1").suspendedToolBatch());
        long firstResultEntries =
                scenario.fixture.conversation.stream()
                        .filter(entry -> entry.messageType() == MessageType.TOOL_RESULT)
                        .count();

        ApexAgent retried = resumeQuestion(scenario);
        assertInstanceOf(AgentRunOutcome.Completed.class, retried.run());
        long retriedResultEntries =
                scenario.fixture.conversation.stream()
                        .filter(entry -> entry.messageType() == MessageType.TOOL_RESULT)
                        .count();
        assertEquals(firstResultEntries, retriedResultEntries);
        assertNull(retried.snapshot().suspendedToolBatch());
    }

    private Scenario questionScenario(Function<PreToolCallContext, PreToolCallHookResult> second) {
        return questionScenario(second, false);
    }

    private Scenario questionScenario(
            Function<PreToolCallContext, PreToolCallHookResult> second, boolean twoCalls) {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.tool(
                "ask",
                (call, context, observer) ->
                        new ToolResult(call.toolCallId(), call.name(), "ok", Map.of()));
        return scenario(
                fixture,
                context -> new RequestHumanIntervention(question(context.toolCall())),
                second,
                twoCalls);
    }

    private Scenario confirmationScenario(
            Function<PreToolCallContext, PreToolCallHookResult> second,
            AtomicReference<Map<String, Object>> arguments) {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.tool(
                "ask",
                (call, context, observer) -> {
                    arguments.set(call.arguments());
                    return new ToolResult(call.toolCallId(), call.name(), "ok", Map.of());
                });
        return scenario(
                fixture,
                context -> new RequestHumanIntervention(confirmation(context)),
                second,
                false);
    }

    private Scenario scenario(
            CoreTestFixture fixture,
            Function<PreToolCallContext, PreToolCallHookResult> first,
            Function<PreToolCallContext, PreToolCallHookResult> second,
            boolean twoCalls) {
        AtomicInteger postCalls = new AtomicInteger();
        fixture.hooks.put("first", preHook(first));
        fixture.hooks.put("second", preHook(second));
        fixture.hooks.put(
                "post",
                new LifecycleHook<PostToolCallContext, PostToolCallHookResult>() {
                    @Override
                    public String name() {
                        return "post";
                    }

                    @Override
                    public HookTypeDescriptor descriptor() {
                        return new HookTypeDescriptor(
                                HookPoint.POST_TOOL_CALL,
                                PostToolCallContext.class,
                                PostToolCallHookResult.class);
                    }

                    @Override
                    public PostToolCallHookResult apply(PostToolCallContext context) {
                        postCalls.incrementAndGet();
                        return new ContinuePostToolCall(
                                HookMutations.none(),
                                new ToolResultPatch(
                                        context.toolResult().content(),
                                        context.toolResult().metadata()));
                    }
                });
        fixture.definition =
                fixture.definition(
                        Map.of(
                                HookPoint.PRE_TOOL_CALL,
                                        List.of(
                                                binding("pre-1", "first", 0),
                                                binding("pre-2", "second", 1)),
                                HookPoint.POST_TOOL_CALL, List.of(binding("post-1", "post", 0))),
                        Set.of("ask"),
                        Set.of("ask"));
        List<ToolCall> calls = new ArrayList<>();
        calls.add(
                new ToolCall(
                        "call-1",
                        "ask",
                        0,
                        Map.of("value", "patched", "room", "A", "locked", "original"),
                        Map.of()));
        if (twoCalls) {
            calls.add(new ToolCall("call-2", "ask", 1, Map.of(), Map.of()));
        }
        fixture.modelResponses.add(new ModelResponse("", calls, Map.of()));
        ApexAgent fresh =
                new ApexAgentFactory()
                        .createNew(
                                new AgentRequest("session-1", "demo", "user-1", "问题"),
                                fixture.ports());
        return new Scenario(fixture, fresh, postCalls);
    }

    private LifecycleHook<PreToolCallContext, PreToolCallHookResult> preHook(
            Function<PreToolCallContext, PreToolCallHookResult> action) {
        return new LifecycleHook<>() {
            @Override
            public String name() {
                return "pre";
            }

            @Override
            public HookTypeDescriptor descriptor() {
                return new HookTypeDescriptor(
                        HookPoint.PRE_TOOL_CALL,
                        PreToolCallContext.class,
                        PreToolCallHookResult.class);
            }

            @Override
            public PreToolCallHookResult apply(PreToolCallContext context) {
                return action.apply(context);
            }
        };
    }

    private HookBinding binding(String id, String name, int order) {
        return new HookBinding(id, name, order, true, List.of("ask"), Map.of());
    }

    private PreToolCallHookResult continued(PreToolCallContext context) {
        return new ContinuePreToolCall(
                HookMutations.none(), new ToolCallPatch(context.toolCall().arguments()));
    }

    private Map<?, ?> toolCallPayload(List<AgentMessageEntry> messages, String toolCallId) {
        for (AgentMessageEntry message : messages) {
            if (message.messageType() != MessageType.TOOL_CALLS
                    || !(message.payload().get("toolCalls") instanceof List<?> calls)) {
                continue;
            }
            for (Object value : calls) {
                if (value instanceof Map<?, ?> call && toolCallId.equals(call.get("toolCallId"))) {
                    return call;
                }
            }
        }
        throw new AssertionError("未找到 ToolCall payload: " + toolCallId);
    }

    private QuestionInterventionRequest question(ToolCall call) {
        return new QuestionInterventionRequest(
                call.toolCallId(), List.of(new QuestionSpec("TEXT_INPUT", "请选择", null, List.of())));
    }

    private ToolConfirmationInterventionRequest confirmation(PreToolCallContext context) {
        ToolConfirmationDetail detail =
                ToolConfirmationDetail.builder()
                        .confirmationId(context.proposedInterventionId())
                        .toolCallId(context.toolCall().toolCallId())
                        .invocationId(context.invocationId())
                        .toolName(context.toolCall().name())
                        .toolDisplayName("ask")
                        .title("确认")
                        .description("确认执行")
                        .riskLevel("MEDIUM")
                        .editable(true)
                        .confirmLabel("批准")
                        .denyLabel("拒绝")
                        .displayFields(List.of())
                        .editableFields(List.of())
                        .build();
        return new ToolConfirmationInterventionRequest(
                context.toolCall().toolCallId(),
                context.proposedInterventionId(),
                context.invocationId(),
                context.toolCall().name(),
                detail,
                Set.of("room"));
    }

    private ApexAgent resumeQuestion(Scenario scenario) {
        scenario.fixture.modelResponses.add(new ModelResponse("完成", List.of(), Map.of()));
        return new ApexAgentFactory()
                .createResumed(
                        command(
                                Map.of(
                                        "interaction_type",
                                        "ASK_HUMAN",
                                        "answers",
                                        Map.of("0", "A"))),
                        scenario.fixture.ports());
    }

    private HumanResponseCommand command(Map<String, Object> response) {
        return new HumanResponseCommand("session-1", "demo", "user-1", Map.of("call-1", response));
    }

    private String confirmationId(Scenario scenario) {
        return ((ToolConfirmationInterventionRequest)
                        scenario.fresh
                                .snapshot()
                                .suspendedToolBatch()
                                .toolCalls()
                                .getFirst()
                                .intervention())
                .confirmationId();
    }

    private List<String> toolContents(CoreTestFixture fixture) {
        return fixture.conversation.stream()
                .filter(entry -> entry.messageType() == MessageType.TOOL_RESULT)
                .map(entry -> entry.content())
                .toList();
    }

    private record Scenario(CoreTestFixture fixture, ApexAgent fresh, AtomicInteger postCalls) {}
}
