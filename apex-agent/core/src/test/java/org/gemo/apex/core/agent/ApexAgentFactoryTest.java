package org.gemo.apex.core.agent;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.gemo.apex.common.agent.AppendPrefixDeveloperMessage;
import org.gemo.apex.common.agent.PrefixDeveloperMessage;
import org.gemo.apex.common.agent.PromptDefinition;
import org.gemo.apex.common.agent.ReplacePrompt;
import org.gemo.apex.common.execution.AgentRequest;
import org.gemo.apex.common.execution.IterationStatus;
import org.gemo.apex.common.execution.SessionStatus;
import org.gemo.apex.common.execution.TurnStatus;
import org.gemo.apex.common.hook.*;
import org.gemo.apex.common.hook.context.AgentBuildContext;
import org.gemo.apex.common.hook.result.AgentBuildHookResult;
import org.gemo.apex.common.hook.result.ContinueAgentBuild;
import org.gemo.apex.common.intervention.HumanResponseCommand;
import org.gemo.apex.common.intervention.QuestionInterventionRequest;
import org.gemo.apex.common.intervention.QuestionSpec;
import org.gemo.apex.common.model.ModelRequest;
import org.gemo.apex.common.model.ModelResponse;
import org.gemo.apex.common.snapshot.*;
import org.gemo.apex.common.tool.ToolAvailabilitySnapshot;
import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.common.tool.ToolResult;
import org.gemo.apex.core.exception.UnavailableToolBindingException;
import org.gemo.apex.extension.hook.LifecycleHook;
import org.junit.jupiter.api.Test;

class ApexAgentFactoryTest {
    /** new按加载构造冻结与持久化顺序执行 */
    @Test
    void newBuildExecutesFrozenAndPersistedOrderAfterLoading() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.hooks.put(
                "build",
                new LifecycleHook<AgentBuildContext, AgentBuildHookResult>() {
                    @Override
                    public String name() {
                        return "build";
                    }

                    @Override
                    public HookTypeDescriptor descriptor() {
                        return new HookTypeDescriptor(
                                HookPoint.AGENT_BUILD,
                                AgentBuildContext.class,
                                AgentBuildHookResult.class);
                    }

                    @Override
                    public AgentBuildHookResult apply(AgentBuildContext context) {
                        fixture.calls.add("hook.build");
                        return new ContinueAgentBuild(
                                List.of(new ReplacePrompt(new PromptDefinition("构造后提示", 30))));
                    }
                });
        fixture.definition =
                fixture.definition(
                        Map.of(
                                HookPoint.AGENT_BUILD,
                                List.of(
                                        new HookBinding(
                                                "build-1", "build", 0, true, List.of(), Map.of()))),
                        Set.of(),
                        Set.of());

        ApexAgent agent =
                new ApexAgentFactory()
                        .createNew(
                                new AgentRequest("session-1", "demo", "user-1", "你好"),
                                fixture.ports());

        assertEquals("构造后提示", agent.snapshot().activeDefinition().prompt().systemPrompt());
        assertTrue(fixture.calls.indexOf("definition.load") < fixture.calls.indexOf("hook.build"));
        assertTrue(fixture.calls.indexOf("hook.build") < fixture.calls.indexOf("tools.load.new"));
        assertTrue(
                fixture.calls.indexOf("conversation.append")
                        < fixture.calls.lastIndexOf("session.save"));
        assertEquals(1, fixture.providerLoads);
    }

    /** resume按当前模板重新加载定义并执行AgentBuild，同时保留原挂起批次。 */
    @Test
    void resumeRebuildsCurrentTemplateAndRetainsSuspendedBatch() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.tool(
                "ask",
                (call, context, observer) ->
                        new ToolResult(call.toolCallId(), call.name(), "ok", Map.of()));
        fixture.definition = fixture.definition(Map.of(), Set.of("ask"), Set.of("ask"));
        AgentPorts ports = fixture.ports();
        ApexAgent fresh =
                new ApexAgentFactory()
                        .createNew(new AgentRequest("session-1", "demo", "user-1", "问题"), ports);
        SessionSnapshot base = fresh.snapshot();
        ToolCall call = new ToolCall("call-1", "ask", 0, Map.of(), Map.of());
        ModelResponse response = new ModelResponse("", List.of(call), Map.of());
        ModelRequest request =
                new ModelRequest(
                        "p", List.of(), List.of(fixture.tools.get("ask").definition()), Map.of());
        IterationSnapshot iteration =
                new IterationSnapshot(
                        1,
                        IterationStatus.SUSPENDED,
                        request,
                        response,
                        List.of(),
                        base.lastActiveTime(),
                        null);
        TurnSnapshot turn =
                new TurnSnapshot(
                        1, TurnStatus.SUSPENDED, iteration, base.activeTurn().startedTime(), null);
        QuestionInterventionRequest intervention =
                new QuestionInterventionRequest(
                        "call-1", List.of(new QuestionSpec("TEXT_INPUT", "?", null, List.of())));
        PreparedToolCallSnapshot prepared =
                new PreparedToolCallSnapshot(
                        "call-1",
                        "inv-1",
                        "ask",
                        0,
                        Map.of(),
                        Map.of(),
                        List.of("hook-1"),
                        PreparedToolCallDisposition.INTERVENTION,
                        null,
                        intervention,
                        null);
        SuspendedToolBatch suspended = new SuspendedToolBatch("session-1", 1, 1, List.of(prepared));
        fixture.sessions.put(
                "session-1",
                new SessionSnapshot(
                        base.schemaVersion(),
                        base.sessionId(),
                        base.userId(),
                        base.agentKey(),
                        SessionStatus.HUMAN_IN_THE_LOOP,
                        1,
                        base.enabledTools(),
                        base.activatedSkills(),
                        base.historicalToolBindings(),
                        base.activeDefinition(),
                        turn,
                        suspended,
                        base.nextMessageSortNo(),
                        base.lastActiveTime()));
        fixture.providerLoads = 0;
        fixture.calls.clear();
        fixture.hooks.put(
                "build",
                new LifecycleHook<AgentBuildContext, AgentBuildHookResult>() {
                    @Override
                    public String name() {
                        return "build";
                    }

                    @Override
                    public HookTypeDescriptor descriptor() {
                        return new HookTypeDescriptor(
                                HookPoint.AGENT_BUILD,
                                AgentBuildContext.class,
                                AgentBuildHookResult.class);
                    }

                    @Override
                    public AgentBuildHookResult apply(AgentBuildContext context) {
                        fixture.calls.add("hook.build");
                        return new ContinueAgentBuild(
                                List.of(
                                        new ReplacePrompt(new PromptDefinition("恢复模板提示", 5)),
                                        new AppendPrefixDeveloperMessage(
                                                new PrefixDeveloperMessage(
                                                        org.gemo.apex.common.message.MessageRole
                                                                .USER,
                                                        "恢复请求前置"))));
                    }
                });
        fixture.definition =
                fixture.definition(
                        Map.of(
                                HookPoint.AGENT_BUILD,
                                List.of(
                                        new HookBinding(
                                                "build-1", "build", 0, true, List.of(), Map.of()))),
                        Set.of("ask"),
                        Set.of("ask"));

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
                                                        Map.of()))),
                                ports);

        assertNotNull(resumed);
        assertEquals("恢复模板提示", resumed.snapshot().activeDefinition().prompt().systemPrompt());
        assertEquals(suspended, resumed.snapshot().suspendedToolBatch());
        assertEquals(1, fixture.providerLoads);
        assertEquals(
                List.of("session.load", "definition.load", "hook.build", "tools.load.new"),
                fixture.calls);

        fixture.modelResponses.add(new ModelResponse("恢复完成", List.of(), Map.of()));
        assertInstanceOf(AgentRunOutcome.Completed.class, resumed.run());
        assertEquals(
                List.of(
                        new PrefixDeveloperMessage(
                                org.gemo.apex.common.message.MessageRole.USER, "恢复请求前置")),
                fixture.modelRequests.getLast().prefixDeveloperMessages());
        assertTrue(
                resumed.snapshot()
                        .activeTurn()
                        .currentIteration()
                        .modelRequest()
                        .prefixDeveloperMessages()
                        .isEmpty());
    }

    /** 新会话绑定不可用工具时拒绝且不产生部分快照 */
    @Test
    void rejectsNewSessionWithUnavailableBoundToolsWithoutCreatingPartialSnapshot() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.definition = fixture.definition(Map.of(), Set.of("offline"), Set.of("offline"));
        fixture.availability = new ToolAvailabilitySnapshot(Set.of("offline"), List.of());

        assertThrows(
                UnavailableToolBindingException.class,
                () ->
                        new ApexAgentFactory()
                                .createNew(
                                        new AgentRequest("session-1", "demo", "user-1", "你好"),
                                        fixture.ports()));
        assertTrue(fixture.sessions.isEmpty());
        assertTrue(fixture.conversation.isEmpty());
    }

    /** 已有会话不可用绑定迁移为历史并移出enabledTools */
    @Test
    void migratesUnavailableBoundToolsToHistoryAndRemovesThemFromEnabledToolsForExistingSession() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.tool(
                "offline",
                (call, context, observer) ->
                        new ToolResult(call.toolCallId(), call.name(), "ok", Map.of()));
        fixture.definition = fixture.definition(Map.of(), Set.of("offline"), Set.of("offline"));
        fixture.modelResponses.add(new ModelResponse("完成", List.of(), Map.of()));
        new ApexAgentFactory()
                .createNew(new AgentRequest("session-1", "demo", "user-1", "第一轮"), fixture.ports())
                .run();
        fixture.tools.clear();
        fixture.availability = new ToolAvailabilitySnapshot(Set.of("offline"), List.of());

        ApexAgent next =
                new ApexAgentFactory()
                        .createNew(
                                new AgentRequest("session-1", "demo", "user-1", "第二轮"),
                                fixture.ports());

        assertFalse(next.snapshot().enabledTools().contains("offline"));
        assertEquals(
                List.of("offline"),
                next.snapshot().historicalToolBindings().stream()
                        .map(binding -> binding.toolName())
                        .toList());
    }
}
