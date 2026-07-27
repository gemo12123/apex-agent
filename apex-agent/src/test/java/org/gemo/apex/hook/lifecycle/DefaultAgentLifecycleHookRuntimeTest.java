package org.gemo.apex.hook.lifecycle;

import org.gemo.apex.config.model.AgentHooksConfig;
import org.gemo.apex.config.model.HookBindingConfig;
import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.definition.agent.AgentDefinition;
import org.gemo.apex.hook.ToolMatcher;
import org.gemo.apex.util.JacksonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.context.ApplicationContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class DefaultAgentLifecycleHookRuntimeTest {

    @Mock
    private ApplicationContext applicationContext;

    private DefaultAgentLifecycleHookRuntime hookRuntime;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        hookRuntime = new DefaultAgentLifecycleHookRuntime(applicationContext, new ToolMatcher());
    }

    @Test
    void shouldExecuteSamePointHooksByOrderAndApplyMessageOperationsImmediately() {
        AgentLifecycleHook later = context -> AgentHookResult.continueWithMessages(
                List.of(MessageOperation.append(new UserMessage("later"))));
        AgentLifecycleHook earlier = context -> AgentHookResult.continueWithMessages(
                List.of(MessageOperation.append(new UserMessage("earlier"))));
        when(applicationContext.getBean("later")).thenReturn(later);
        when(applicationContext.getBean("earlier")).thenReturn(earlier);

        AgentRuntimeContext runtime = runtime(AgentHooksConfig.builder()
                .preModelCall(List.of(
                        binding("later", 20),
                        binding("earlier", 10)))
                .build());

        HookDispatchResult result = hookRuntime.run(HookPoint.PRE_MODEL_CALL, runtime);

        assertEquals(HookFlowAction.CONTINUE, result.getResult().getAction());
        assertEquals(List.of("earlier", "later"), result.getExecutedHookBeans());
        assertEquals(List.of("initial", "earlier", "later"),
                runtime.getWorkingMessages().stream().map(message -> message.getText()).toList());
        assertEquals(2, runtime.getIteration().getMessageMutations().size());
        assertTrue(runtime.getIteration().getMessageMutations().stream().allMatch(MessageMutationRecord::isApplied));
    }

    @Test
    void shouldRecordHookAndMessageOperationFailuresAndContinueWithLaterHooks() {
        AgentLifecycleHook badIndex = context -> AgentHookResult.continueWithMessages(
                List.of(
                        MessageOperation.delete(99),
                        MessageOperation.append(new UserMessage("after-invalid"))));
        AgentLifecycleHook throwing = context -> {
            throw new IllegalStateException("boom");
        };
        AgentLifecycleHook good = context -> AgentHookResult.continueWithMessages(
                List.of(MessageOperation.replace(0, new UserMessage("replaced"))));
        when(applicationContext.getBean("badIndex")).thenReturn(badIndex);
        when(applicationContext.getBean("throwing")).thenReturn(throwing);
        when(applicationContext.getBean("good")).thenReturn(good);

        AgentRuntimeContext runtime = runtime(AgentHooksConfig.builder()
                .preModelCall(List.of(
                        binding("badIndex", 1),
                        binding("throwing", 2),
                        binding("good", 3)))
                .build());

        HookDispatchResult result = hookRuntime.run(HookPoint.PRE_MODEL_CALL, runtime);

        assertEquals(List.of("good"), result.getExecutedHookBeans());
        assertEquals("replaced", runtime.getWorkingMessages().getFirst().getText());
        assertEquals("after-invalid", runtime.getWorkingMessages().getLast().getText());
        assertFalse(runtime.getIteration().getMessageMutations().getFirst().isApplied());
        assertEquals(3, runtime.getIteration().getHookExecutions().size());
        assertEquals(2, runtime.getIteration().getHookExecutions().stream()
                .filter(record -> !record.isSucceeded())
                .count());
    }

    @Test
    void shouldIgnoreFlowControlReturnedFromNonToolPoint() {
        AgentLifecycleHook invalid = context -> AgentHookResult.endTurn(List.of());
        AgentLifecycleHook good = context -> AgentHookResult.continueWithMessages(
                List.of(MessageOperation.append(new UserMessage("continued"))));
        when(applicationContext.getBean("invalid")).thenReturn(invalid);
        when(applicationContext.getBean("good")).thenReturn(good);
        AgentRuntimeContext runtime = runtime(AgentHooksConfig.builder()
                .iterationStart(List.of(binding("invalid", 1), binding("good", 2)))
                .build());

        HookDispatchResult result = hookRuntime.run(HookPoint.ITERATION_START, runtime);

        assertEquals(HookFlowAction.CONTINUE, result.getResult().getAction());
        assertEquals("continued", runtime.getWorkingMessages().getLast().getText());
        assertFalse(runtime.getIteration().getHookExecutions().getFirst().isSucceeded());
    }

    @Test
    void shouldAuditTurnStartMessageOperationsBeforeFirstIteration() {
        AgentLifecycleHook hook = context -> AgentHookResult.continueWithMessages(
                List.of(MessageOperation.append(new UserMessage("turn-start"))));
        when(applicationContext.getBean("turnStart")).thenReturn(hook);
        AgentRuntimeContext runtime = runtime(AgentHooksConfig.builder()
                .turnStart(List.of(binding("turnStart", 1)))
                .build());
        runtime.setIteration(null);

        hookRuntime.run(HookPoint.TURN_START, runtime);

        assertEquals("turn-start", runtime.getWorkingMessages().getLast().getText());
        assertEquals(1, runtime.getTurn().getMessageMutations().size());
        assertTrue(runtime.getTurn().getMessageMutations().getFirst().isApplied());
    }

    @Test
    void iterationAuditPayloadShouldRoundTrip() {
        AgentIteration iteration = AgentIteration.builder()
                .turnNo(10L)
                .iterationNo(2)
                .modelInput(new ArrayList<>(List.of(new UserMessage("input"))))
                .originalModelOutput(new ChatResponse(List.of(
                        new Generation(new AssistantMessage("raw-output")))))
                .finalModelOutput(new AssistantMessage("final-output"))
                .toolCalls(new ArrayList<>(List.of(ToolCallRecord.builder()
                        .toolCallId("call-1")
                        .toolName("demo")
                        .succeeded(true)
                        .build())))
                .startedAt(LocalDateTime.now())
                .build();

        String payload = JacksonUtils.toJson(iteration);
        AgentIteration restored = JacksonUtils.fromJson(payload, AgentIteration.class);

        assertNotNull(restored);
        assertTrue(payload.contains("\"iterationNo\":2"));
        assertFalse(payload.contains("traceNo"));
        assertEquals(10L, restored.getTurnNo());
        assertEquals(2, restored.getIterationNo());
        assertEquals("input", restored.getModelInput().getFirst().getText());
        assertEquals("raw-output", restored.getOriginalModelOutput().getResult().getOutput().getText());
        assertEquals("final-output", restored.getFinalModelOutput().getText());
        assertEquals("call-1", restored.getToolCalls().getFirst().getToolCallId());
    }

    private AgentRuntimeContext runtime(AgentHooksConfig hooks) {
        SuperAgentContext session = new SuperAgentContext();
        session.setSessionId("session-1");
        session.setAgentKey("agent-1");
        session.setUserId("user-1");
        AgentTurn turn = AgentTurn.builder()
                .turnNo(10L)
                .sessionId("session-1")
                .agentKey("agent-1")
                .startedAt(LocalDateTime.now())
                .build();
        AgentIteration iteration = AgentIteration.builder()
                .turnNo(10L)
                .iterationNo(1)
                .startedAt(LocalDateTime.now())
                .build();
        return AgentRuntimeContext.builder()
                .sessionContext(session)
                .agentDefinition(new AgentDefinition("agent-1", ModeEnum.REACT, List.of(), List.of(), List.of(),
                        hooks, "", "", "", ""))
                .turn(turn)
                .iteration(iteration)
                .workingMessages(new ArrayList<>(List.of(new UserMessage("initial"))))
                .build();
    }

    private HookBindingConfig binding(String bean, int order) {
        return HookBindingConfig.builder().bean(bean).order(order).build();
    }
}
