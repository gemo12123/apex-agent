package org.gemo.apex.core.agent;

import org.gemo.apex.common.execution.AgentRequest;
import org.gemo.apex.common.execution.SessionStatus;
import org.gemo.apex.common.message.MessageType;
import org.gemo.apex.common.hook.*;
import org.gemo.apex.common.hook.context.PreToolCallContext;
import org.gemo.apex.common.hook.operation.HookMutations;
import org.gemo.apex.common.hook.operation.ToolActivationDelta;
import org.gemo.apex.common.hook.operation.ToolCallPatch;
import org.gemo.apex.common.hook.result.ContinuePreToolCall;
import org.gemo.apex.common.hook.result.PreToolCallHookResult;
import org.gemo.apex.extension.hook.LifecycleHook;
import org.gemo.apex.common.model.ModelResponse;
import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.common.tool.ToolResult;
import org.gemo.apex.protocol.event.EndMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ApexAgentExecutionTest {
    @Test
    void 无工具响应完成单次ReAct并只请求一次End() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.modelResponses.add(new ModelResponse("完成", List.of(), Map.of()));
        ApexAgent agent = create(fixture);

        AgentRunOutcome outcome = agent.run();

        assertInstanceOf(AgentRunOutcome.Completed.class, outcome, outcome.toString());
        assertEquals(SessionStatus.COMPLETED, agent.snapshot().status());
        assertEquals(1, fixture.modelCalls);
        assertEquals(1, fixture.events.stream().filter(EndMessage.class::isInstance).count());
    }

    @Test
    void 多ToolCall按序执行并在工具异常后继续下一轮模型() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.tool("ok", (call, context, observer) ->
                new ToolResult(call.toolCallId(), call.name(), "成功", Map.of()));
        fixture.tool("bad", (call, context, observer) -> { throw new IllegalStateException("boom"); });
        fixture.definition = fixture.definition(Map.of(), Set.of("ok", "bad"), Set.of("ok", "bad"));
        fixture.modelResponses.add(new ModelResponse("", List.of(
                new ToolCall("c1", "ok", 0, Map.of(), Map.of()),
                new ToolCall("c2", "bad", 1, Map.of(), Map.of())), Map.of()));
        fixture.modelResponses.add(new ModelResponse("最终", List.of(), Map.of()));
        ApexAgent agent = create(fixture);

        AgentRunOutcome outcome = agent.run();

        assertInstanceOf(AgentRunOutcome.Completed.class, outcome, outcome.toString());
        assertEquals(2, fixture.toolCalls);
        List<String> toolContents = fixture.conversation.stream()
                .filter(entry -> entry.messageType() == MessageType.TOOL_RESULT)
                .map(entry -> entry.content()).toList();
        assertEquals(List.of("成功", "工具执行失败：IllegalStateException"), toolContents);
        assertEquals(2, fixture.modelCalls);
    }

    @Test
    void 最后一轮仍返回工具时不执行工具并补齐固定结果() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.tool("tool", (call, context, observer) ->
                new ToolResult(call.toolCallId(), call.name(), "真实结果", Map.of()));
        fixture.definition = fixture.definition(Map.of(), Set.of("tool"), Set.of("tool"));
        for (int i = 0; i < 3; i++) {
            fixture.modelResponses.add(new ModelResponse("", List.of(
                    new ToolCall("c" + i, "tool", 0, Map.of(), Map.of())), Map.of()));
        }
        ApexAgent agent = create(fixture);

        AgentRunOutcome outcome = agent.run();

        assertInstanceOf(AgentRunOutcome.EndedByHook.class, outcome, outcome.toString());
        assertEquals(3, fixture.modelCalls);
        assertEquals(2, fixture.toolCalls);
        assertTrue(fixture.modelRequests.getLast().systemPrompt()
                .contains("直接输出最终结论且不再调用工具"));
        assertEquals("达到最大轮次，强制结束", fixture.conversation.stream()
                .filter(entry -> entry.messageType() == MessageType.TOOL_RESULT).toList().getLast().content());
    }

    @Test
    void 工具期间取消会停止剩余工具并批量补齐取消结果() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.tool("cancel", (call, context, observer) -> {
            fixture.token.cancel();
            context.cancellationToken().throwIfCancellationRequested();
            throw new AssertionError();
        });
        fixture.definition = fixture.definition(Map.of(), Set.of("cancel"), Set.of("cancel"));
        fixture.modelResponses.add(new ModelResponse("", List.of(
                new ToolCall("c1", "cancel", 0, Map.of(), Map.of()),
                new ToolCall("c2", "cancel", 1, Map.of(), Map.of())), Map.of()));
        ApexAgent agent = create(fixture);

        AgentRunOutcome outcome = agent.run();

        assertInstanceOf(AgentRunOutcome.Cancelled.class, outcome, outcome.toString());
        assertEquals(SessionStatus.CANCELLED, agent.snapshot().status());
        assertEquals(1, fixture.toolCalls);
        assertEquals(List.of("请求已取消，工具未执行完成", "请求已取消，工具未执行完成"),
                fixture.conversation.stream().filter(entry -> entry.messageType() == MessageType.TOOL_RESULT)
                        .map(entry -> entry.content()).toList());
    }

    @Test
    void 压缩门按compact再session保存后才调用模型() {
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
    }

    @Test
    void 模型失败后三层失败且不执行结束生命周期() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.modelFailure = new IllegalStateException("model down");
        ApexAgent agent = create(fixture);

        AgentRunOutcome outcome = agent.run();

        assertInstanceOf(AgentRunOutcome.Failed.class, outcome);
        assertEquals(SessionStatus.FAILED, agent.snapshot().status());
        assertEquals(org.gemo.apex.common.execution.TurnStatus.FAILED, agent.snapshot().activeTurn().status());
        assertEquals(org.gemo.apex.common.execution.IterationStatus.FAILED,
                agent.snapshot().activeTurn().currentIteration().status());
    }

    @Test
    void prepared取消不创建Iteration或调用模型工具Hook() {
        CoreTestFixture fixture = new CoreTestFixture();
        ApexAgent agent = create(fixture);

        AgentRunOutcome outcome = agent.cancelBeforeRun();

        assertInstanceOf(AgentRunOutcome.Cancelled.class, outcome);
        assertEquals(SessionStatus.CANCELLED, agent.snapshot().status());
        assertNull(agent.snapshot().activeTurn().currentIteration());
        assertEquals(0, fixture.modelCalls);
        assertEquals(0, fixture.toolCalls);
    }

    @Test
    void Hook中途禁用会阻止同一响应中的后续伪造调用() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.tool("first", (call, context, observer) ->
                new ToolResult(call.toolCallId(), call.name(), "first-ok", Map.of()));
        fixture.tool("second", (call, context, observer) ->
                new ToolResult(call.toolCallId(), call.name(), "second-ok", Map.of()));
        fixture.hooks.put("disable-second", new LifecycleHook<PreToolCallContext, PreToolCallHookResult>() {
            @Override public HookTypeDescriptor descriptor() {
                return new HookTypeDescriptor(HookPoint.PRE_TOOL_CALL, PreToolCallContext.class,
                        PreToolCallHookResult.class);
            }
            @Override public PreToolCallHookResult apply(PreToolCallContext context) {
                return new ContinuePreToolCall(new HookMutations(List.of(),
                        new ToolActivationDelta(Set.of(), Set.of("second"))),
                        new ToolCallPatch(context.toolCall().arguments()));
            }
        });
        fixture.definition = fixture.definition(Map.of(HookPoint.PRE_TOOL_CALL,
                        List.of(new HookBinding("disable", "disable-second", 0, true,
                                List.of("first"), Map.of()))),
                Set.of("first", "second"), Set.of("first", "second"));
        fixture.modelResponses.add(new ModelResponse("", List.of(
                new ToolCall("c1", "first", 0, Map.of(), Map.of()),
                new ToolCall("c2", "second", 1, Map.of(), Map.of())), Map.of()));
        fixture.modelResponses.add(new ModelResponse("完成", List.of(), Map.of()));

        AgentRunOutcome outcome = create(fixture).run();

        assertInstanceOf(AgentRunOutcome.Completed.class, outcome, outcome.toString());
        assertEquals(1, fixture.toolCalls);
        assertEquals("工具当前未启用，无法执行", fixture.conversation.stream()
                .filter(entry -> entry.messageType() == MessageType.TOOL_RESULT).toList().getLast().content());
    }

    @Test
    void 工具发布非白名单事件会转换为当前工具失败结果() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.tool("bad-event", (call, context, observer) -> {
            observer.onEvent(org.gemo.apex.protocol.event.EndMessage.builder().build());
            return new ToolResult(call.toolCallId(), call.name(), "不应到达", Map.of());
        });
        fixture.definition = fixture.definition(Map.of(), Set.of("bad-event"), Set.of("bad-event"));
        fixture.modelResponses.add(new ModelResponse("", List.of(
                new ToolCall("c1", "bad-event", 0, Map.of(), Map.of())), Map.of()));
        fixture.modelResponses.add(new ModelResponse("完成", List.of(), Map.of()));

        AgentRunOutcome outcome = create(fixture).run();

        assertInstanceOf(AgentRunOutcome.Completed.class, outcome, outcome.toString());
        assertEquals("工具执行失败：IllegalToolEventException", fixture.conversation.stream()
                .filter(entry -> entry.messageType() == MessageType.TOOL_RESULT).findFirst().orElseThrow().content());
    }

    private ApexAgent create(CoreTestFixture fixture) {
        return new ApexAgentFactory().createNew(
                new AgentRequest("session-1", "demo", "user-1", "你好"), fixture.ports());
    }
}
