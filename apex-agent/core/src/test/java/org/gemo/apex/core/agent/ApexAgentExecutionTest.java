package org.gemo.apex.core.agent;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.gemo.apex.common.agent.*;
import org.gemo.apex.common.execution.AgentRequest;
import org.gemo.apex.common.execution.IterationStatus;
import org.gemo.apex.common.execution.SessionStatus;
import org.gemo.apex.common.execution.TurnStatus;
import org.gemo.apex.common.hook.*;
import org.gemo.apex.common.hook.context.AgentBuildContext;
import org.gemo.apex.common.hook.context.PostMessageCompressionContext;
import org.gemo.apex.common.hook.context.PostToolCallContext;
import org.gemo.apex.common.hook.context.PreToolCallContext;
import org.gemo.apex.common.hook.operation.AppendMessage;
import org.gemo.apex.common.hook.operation.ConversationCompactionResultPatch;
import org.gemo.apex.common.hook.operation.HookMutations;
import org.gemo.apex.common.hook.operation.SkillActivationDelta;
import org.gemo.apex.common.hook.operation.ToolActivationDelta;
import org.gemo.apex.common.hook.operation.ToolCallPatch;
import org.gemo.apex.common.hook.operation.ToolResultPatch;
import org.gemo.apex.common.hook.result.AgentBuildHookResult;
import org.gemo.apex.common.hook.result.ContinueAgentBuild;
import org.gemo.apex.common.hook.result.ContinuePostMessageCompression;
import org.gemo.apex.common.hook.result.ContinuePostToolCall;
import org.gemo.apex.common.hook.result.ContinuePreToolCall;
import org.gemo.apex.common.hook.result.EndTurnPostMessageCompression;
import org.gemo.apex.common.hook.result.PostMessageCompressionHookResult;
import org.gemo.apex.common.hook.result.PostToolCallHookResult;
import org.gemo.apex.common.hook.result.PreToolCallHookResult;
import org.gemo.apex.common.message.MessageRole;
import org.gemo.apex.common.message.MessageType;
import org.gemo.apex.common.model.ModelResponse;
import org.gemo.apex.common.snapshot.ExecutionErrorType;
import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.common.tool.ToolResult;
import org.gemo.apex.extension.hook.LifecycleHook;
import org.gemo.apex.protocol.event.EndMessage;
import org.gemo.apex.protocol.event.InvocationChangeMessage;
import org.gemo.apex.protocol.event.InvocationDeclaredMessage;
import org.gemo.apex.protocol.event.TaskErrorMessage;
import org.junit.jupiter.api.Test;

class ApexAgentExecutionTest {
    /** 多个PRE Hook逐项看到前序Patch，最终参数仅作为审计字段写入 */
    @Test
    void auditsFinalResolvedArgumentsWithoutReplacingModelArguments() {
        CoreTestFixture fixture = new CoreTestFixture();
        AtomicReference<Map<String, Object>> executedArguments = new AtomicReference<>();
        fixture.tool(
                "weather",
                (call, context, observer) -> {
                    executedArguments.set(call.arguments());
                    return new ToolResult(call.toolCallId(), call.name(), "晴", Map.of());
                });
        fixture.hooks.put(
                "first",
                preToolHook(
                        context -> {
                            Map<String, Object> arguments =
                                    new LinkedHashMap<>(context.toolCall().arguments());
                            arguments.put("city", "北京");
                            return new ContinuePreToolCall(
                                    HookMutations.none(), new ToolCallPatch(arguments));
                        }));
        fixture.hooks.put(
                "second",
                preToolHook(
                        context -> {
                            assertEquals("北京", context.toolCall().arguments().get("city"));
                            Map<String, Object> arguments =
                                    new LinkedHashMap<>(context.toolCall().arguments());
                            arguments.put("timeout", 3);
                            return new ContinuePreToolCall(
                                    HookMutations.none(), new ToolCallPatch(arguments));
                        }));
        fixture.definition =
                fixture.definition(
                        Map.of(
                                HookPoint.PRE_TOOL_CALL,
                                List.of(
                                        new HookBinding(
                                                "second",
                                                "second",
                                                20,
                                                true,
                                                List.of("weather"),
                                                Map.of()),
                                        new HookBinding(
                                                "first",
                                                "first",
                                                10,
                                                true,
                                                List.of("weather"),
                                                Map.of()))),
                        Set.of("weather"),
                        Set.of("weather"));
        Map<String, Object> original = Map.of("city", "上海", "unit", "C");
        Map<String, Object> resolved = Map.of("city", "北京", "unit", "C", "timeout", 3);
        fixture.modelResponses.add(
                new ModelResponse(
                        "",
                        List.of(new ToolCall("call-1", "weather", 0, original, Map.of())),
                        Map.of()));
        fixture.modelResponses.add(new ModelResponse("完成", List.of(), Map.of()));

        AgentRunOutcome outcome = create(fixture).run();

        assertInstanceOf(AgentRunOutcome.Completed.class, outcome, outcome.toString());
        assertEquals(resolved, executedArguments.get());
        Map<?, ?> audited = toolCallPayload(fixture.conversation, "call-1");
        assertEquals(original, audited.get("arguments"));
        assertEquals(resolved, audited.get("resolvedArguments"));
        Map<?, ?> nextRequest =
                toolCallPayload(fixture.modelRequests.getLast().messages(), "call-1");
        assertEquals(original, nextRequest.get("arguments"));
        assertEquals(resolved, nextRequest.get("resolvedArguments"));
        InvocationDeclaredMessage declared =
                fixture.events.stream()
                        .filter(InvocationDeclaredMessage.class::isInstance)
                        .map(InvocationDeclaredMessage.class::cast)
                        .findFirst()
                        .orElseThrow();
        assertEquals("weather", declared.getContext().get("executor"));
        assertEquals(
                resolved,
                org.gemo.apex.common.json.JsonUtils.fromJson(
                        declared.getMessages().getFirst().getContent(), Map.class));
    }

    @Test
    void publishesPostHookResultOnlyAfterItWasPersisted() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.tool(
                "tool",
                (call, context, observer) ->
                        new ToolResult(call.toolCallId(), call.name(), "原始结果", Map.of()));
        fixture.hooks.put(
                "post",
                postToolHook(context -> new ToolResultPatch("POST最终结果", Map.of("checked", true))));
        fixture.definition =
                fixture.definition(
                        Map.of(
                                HookPoint.POST_TOOL_CALL,
                                List.of(
                                        new HookBinding(
                                                "post",
                                                "post",
                                                0,
                                                true,
                                                List.of("tool"),
                                                Map.of()))),
                        Set.of("tool"),
                        Set.of("tool"));
        fixture.modelResponses.add(
                new ModelResponse(
                        "",
                        List.of(new ToolCall("call-1", "tool", 0, Map.of(), Map.of())),
                        Map.of()));
        fixture.modelResponses.add(new ModelResponse("完成", List.of(), Map.of()));

        assertInstanceOf(AgentRunOutcome.Completed.class, create(fixture).run());

        InvocationChangeMessage changed =
                fixture.events.stream()
                        .filter(InvocationChangeMessage.class::isInstance)
                        .map(InvocationChangeMessage.class::cast)
                        .findFirst()
                        .orElseThrow();
        assertEquals("POST最终结果", changed.getMessages().getFirst().getContent());
        assertEquals("COMPLETE", changed.getMessages().getLast().getStatus());
        int changeIndex = fixture.calls.indexOf("event.InvocationChangeMessage");
        assertEquals("session.save", fixture.calls.get(changeIndex - 1));
    }

    @Test
    void doesNotExecuteWhenInvocationDeclarationCannotBePublished() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.tool(
                "tool",
                (call, context, observer) ->
                        new ToolResult(call.toolCallId(), call.name(), "不应执行", Map.of()));
        fixture.definition = fixture.definition(Map.of(), Set.of("tool"), Set.of("tool"));
        fixture.failInvocationDeclaredPublish = true;
        fixture.modelResponses.add(
                new ModelResponse(
                        "",
                        List.of(new ToolCall("call-1", "tool", 0, Map.of(), Map.of())),
                        Map.of()));

        assertInstanceOf(AgentRunOutcome.Failed.class, create(fixture).run());
        assertEquals(0, fixture.toolCalls);
    }

    @Test
    void doesNotPublishTerminalInvocationWhenToolResultPersistenceFails() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.tool(
                "tool",
                (call, context, observer) ->
                        new ToolResult(call.toolCallId(), call.name(), "结果", Map.of()));
        fixture.definition = fixture.definition(Map.of(), Set.of("tool"), Set.of("tool"));
        fixture.failToolResultAppend = true;
        fixture.modelResponses.add(
                new ModelResponse(
                        "",
                        List.of(new ToolCall("call-1", "tool", 0, Map.of(), Map.of())),
                        Map.of()));

        assertInstanceOf(AgentRunOutcome.Failed.class, create(fixture).run());
        assertEquals(
                1,
                fixture.events.stream()
                        .filter(InvocationDeclaredMessage.class::isInstance)
                        .count());
        assertEquals(
                0,
                fixture.events.stream().filter(InvocationChangeMessage.class::isInstance).count());
    }

    /** 最终参数审计写入失败时不得执行真实工具 */
    @Test
    void preventsToolExecutionWhenResolvedArgumentAuditCannotBePersisted() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.tool(
                "tool",
                (call, context, observer) ->
                        new ToolResult(call.toolCallId(), call.name(), "不应执行", Map.of()));
        fixture.definition = fixture.definition(Map.of(), Set.of("tool"), Set.of("tool"));
        fixture.failToolCallAuditReplace = true;
        fixture.modelResponses.add(
                new ModelResponse(
                        "",
                        List.of(new ToolCall("call-1", "tool", 0, Map.of("value", "x"), Map.of())),
                        Map.of()));

        AgentRunOutcome outcome = create(fixture).run();

        assertInstanceOf(AgentRunOutcome.Failed.class, outcome);
        assertEquals(0, fixture.toolCalls);
        assertTrue(
                fixture.conversation.stream()
                        .noneMatch(message -> message.messageType() == MessageType.TOOL_RESULT));
    }

    /** AGENT_BUILD按固定顺序追加前置消息，同一请求各Iteration复用且快照不保存 */
    @Test
    void appendsPrefixDeveloperMessagesOncePerRequestAndExcludesThemFromSnapshot() {
        CoreTestFixture fixture = new CoreTestFixture();
        AtomicInteger buildCalls = new AtomicInteger();
        PrefixDeveloperMessage system = new PrefixDeveloperMessage(MessageRole.SYSTEM, "系统前置");
        PrefixDeveloperMessage user = new PrefixDeveloperMessage(MessageRole.USER, "用户前置");
        fixture.hooks.put(
                "prefix-first",
                buildHook(
                        context -> {
                            buildCalls.incrementAndGet();
                            assertTrue(context.definition().prefixDeveloperMessages().isEmpty());
                            return List.of(
                                    new AppendPrefixDeveloperMessage(system),
                                    new AppendPrefixDeveloperMessage(system));
                        }));
        fixture.hooks.put(
                "prefix-second",
                buildHook(
                        context -> {
                            buildCalls.incrementAndGet();
                            assertEquals(
                                    List.of(system, system),
                                    context.definition().prefixDeveloperMessages());
                            return List.of(new AppendPrefixDeveloperMessage(user));
                        }));
        fixture.tool(
                "tool",
                (call, context, observer) ->
                        new ToolResult(call.toolCallId(), call.name(), "结果", Map.of()));
        fixture.definition =
                fixture.definition(
                        Map.of(
                                HookPoint.AGENT_BUILD,
                                List.of(
                                        new HookBinding(
                                                "second",
                                                "prefix-second",
                                                20,
                                                true,
                                                List.of(),
                                                Map.of()),
                                        new HookBinding(
                                                "first",
                                                "prefix-first",
                                                10,
                                                true,
                                                List.of(),
                                                Map.of()))),
                        Set.of("tool"),
                        Set.of("tool"));
        fixture.modelResponses.add(
                new ModelResponse(
                        "",
                        List.of(new ToolCall("call", "tool", 0, Map.of(), Map.of())),
                        Map.of()));
        fixture.modelResponses.add(new ModelResponse("完成", List.of(), Map.of()));

        ApexAgent agent = create(fixture);
        assertInstanceOf(AgentRunOutcome.Completed.class, agent.run());

        assertEquals(2, buildCalls.get());
        assertEquals(2, fixture.modelRequests.size());
        assertTrue(
                fixture.modelRequests.stream()
                        .allMatch(
                                request ->
                                        request.prefixDeveloperMessages()
                                                .equals(List.of(system, system, user))));
        assertTrue(
                agent.snapshot()
                        .activeTurn()
                        .currentIteration()
                        .modelRequest()
                        .prefixDeveloperMessages()
                        .isEmpty());
        assertTrue(
                fixture.sessions
                        .get("session-1")
                        .activeTurn()
                        .currentIteration()
                        .modelRequest()
                        .prefixDeveloperMessages()
                        .isEmpty());
        assertTrue(
                fixture.conversation.stream()
                        .noneMatch(
                                message ->
                                        "系统前置".equals(message.content())
                                                || "用户前置".equals(message.content())));
    }

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

    /** 同一次execution只加载一次窗口，后续Iteration使用Context中的写穿结果。 */
    @Test
    void loadsConversationWindowOnceAndReusesWriteThroughMessagesAcrossIterations() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.tool(
                "tool",
                (call, context, observer) ->
                        new ToolResult(call.toolCallId(), call.name(), "工具结果", Map.of()));
        fixture.definition = fixture.definition(Map.of(), Set.of("tool"), Set.of("tool"));
        fixture.modelResponses.add(
                new ModelResponse(
                        "",
                        List.of(new ToolCall("call-1", "tool", 0, Map.of(), Map.of())),
                        Map.of()));
        fixture.modelResponses.add(new ModelResponse("完成", List.of(), Map.of()));

        AgentRunOutcome outcome = create(fixture).run();

        assertInstanceOf(AgentRunOutcome.Completed.class, outcome);
        assertEquals(1, fixture.windowLoads);
        assertEquals(
                List.of(MessageRole.USER),
                fixture.modelRequests.getFirst().messages().stream()
                        .map(message -> message.role())
                        .toList());
        assertEquals(
                List.of(MessageRole.USER, MessageRole.ASSISTANT, MessageRole.TOOL),
                fixture.modelRequests.getLast().messages().stream()
                        .map(message -> message.role())
                        .toList());
        assertEquals(
                List.of(MessageType.TEXT, MessageType.TOOL_CALLS, MessageType.TOOL_RESULT),
                fixture.modelRequests.getLast().messages().stream()
                        .map(message -> message.messageType())
                        .toList());
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
        assertEquals(List.of("成功", "工具执行失败：boom"), toolContents);
        assertEquals(2, fixture.modelCalls);
        assertEquals(
                List.of("COMPLETE", "FAILED"),
                fixture.events.stream()
                        .filter(InvocationChangeMessage.class::isInstance)
                        .map(InvocationChangeMessage.class::cast)
                        .map(event -> event.getMessages().getLast().getStatus())
                        .toList());
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
        assertTrue(toolCallPayload(fixture.conversation, "c1").containsKey("resolvedArguments"));
        assertTrue(toolCallPayload(fixture.conversation, "c2").containsKey("resolvedArguments"));
        assertEquals(
                1,
                fixture.events.stream()
                        .filter(InvocationDeclaredMessage.class::isInstance)
                        .count());
        InvocationChangeMessage cancelled =
                fixture.events.stream()
                        .filter(InvocationChangeMessage.class::isInstance)
                        .map(InvocationChangeMessage.class::cast)
                        .findFirst()
                        .orElseThrow();
        assertEquals("CANCELLED", cancelled.getMessages().getLast().getStatus());
    }

    /** 压缩门按compact再session保存后才调用模型 */
    @Test
    void callsModelOnlyAfterCompactionGateAndSessionSave() {
        CoreTestFixture fixture = new CoreTestFixture();
        PrefixDeveloperMessage prefix =
                new PrefixDeveloperMessage(MessageRole.SYSTEM, "不会进入摘要的前置消息");
        fixture.hooks.put(
                "prefix", buildHook(context -> List.of(new AppendPrefixDeveloperMessage(prefix))));
        fixture.definition =
                fixture.definition(
                        Map.of(
                                HookPoint.AGENT_BUILD,
                                List.of(
                                        new HookBinding(
                                                "prefix", "prefix", 0, true, List.of(), Map.of()))),
                        Set.of(),
                        Set.of());
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
        assertEquals(List.of(prefix), fixture.modelRequests.getFirst().prefixDeveloperMessages());
        assertEquals(1, fixture.compactionCheck.messages().size());
        assertTrue(
                fixture.compactionCheck.messageCharacterEstimate()
                        >= prefix.content().length() + "你好".length());
        assertTrue(
                fixture.compactionRequest.sourceMessages().stream()
                        .noneMatch(message -> prefix.content().equals(message.content())));
    }

    /** 压缩后的下一Iteration直接复用Context窗口，不重新加载会话历史。 */
    @Test
    void reusesCompactedContextWindowWithoutReloadingOnNextIteration() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.compact = true;
        fixture.tool(
                "tool",
                (call, context, observer) ->
                        new ToolResult(call.toolCallId(), call.name(), "工具结果", Map.of()));
        fixture.definition = fixture.definition(Map.of(), Set.of("tool"), Set.of("tool"));
        fixture.modelResponses.add(
                new ModelResponse(
                        "",
                        List.of(new ToolCall("call-1", "tool", 0, Map.of(), Map.of())),
                        Map.of()));
        fixture.modelResponses.add(new ModelResponse("完成", List.of(), Map.of()));

        AgentRunOutcome outcome = create(fixture).run();

        assertInstanceOf(AgentRunOutcome.Completed.class, outcome);
        assertEquals(1, fixture.windowLoads);
        assertEquals(
                List.of(MessageType.SUMMARY, MessageType.TOOL_RESULT),
                fixture.modelRequests.getLast().messages().stream()
                        .map(message -> message.messageType())
                        .toList());
    }

    /** POST压缩Hook按Binding顺序持久化追加并进入本次模型请求 */
    @Test
    void persistsPostCompressionHookAppendsInBindingOrderBeforeModelCall() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.compact = true;
        fixture.hooks.put("append-a", postCompressionAppendHook("a", "第一条"));
        fixture.hooks.put("append-b", postCompressionAppendHook("b", "第二条"));
        fixture.definition =
                fixture.definition(
                        Map.of(
                                HookPoint.POST_MESSAGE_COMPRESSION,
                                List.of(
                                        new HookBinding(
                                                "b", "append-b", 20, true, List.of(), Map.of()),
                                        new HookBinding(
                                                "a", "append-a", 10, true, List.of(), Map.of()))),
                        Set.of(),
                        Set.of());
        fixture.modelResponses.add(new ModelResponse("完成", List.of(), Map.of()));

        AgentRunOutcome outcome = create(fixture).run();

        assertInstanceOf(AgentRunOutcome.Completed.class, outcome);
        assertEquals(
                List.of("摘要", "第一条", "第二条"),
                fixture.modelRequests.getFirst().messages().stream()
                        .map(message -> message.content())
                        .toList());
        assertEquals(
                List.of("你好", "第一条", "第二条", "完成"),
                fixture.conversation.stream().map(message -> message.content()).toList());
        assertTrue(
                fixture.calls.indexOf("conversation.compact")
                        < fixture.calls.lastIndexOf("conversation.append"));
    }

    /** POST压缩Hook消息写入失败时摘要与消息操作共同回滚 */
    @Test
    void rollsBackCompactionWhenPostCompressionAppendFails() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.compact = true;
        fixture.hooks.put("append", postCompressionAppendHook("append", "Hook补充"));
        fixture.definition =
                fixture.definition(
                        Map.of(
                                HookPoint.POST_MESSAGE_COMPRESSION,
                                List.of(
                                        new HookBinding(
                                                "append", "append", 0, true, List.of(), Map.of()))),
                        Set.of(),
                        Set.of());
        fixture.modelResponses.add(new ModelResponse("不会调用", List.of(), Map.of()));
        ApexAgent agent = create(fixture);
        fixture.failPostCompressionAppend = true;

        AgentRunOutcome outcome = agent.run();

        assertInstanceOf(AgentRunOutcome.Failed.class, outcome);
        assertNull(fixture.compactionCommit);
        assertNull(fixture.summary);
        assertTrue(
                fixture.conversation.stream()
                        .noneMatch(message -> "Hook补充".equals(message.content())));
        assertEquals(SessionStatus.FAILED, agent.snapshot().status());
        assertEquals(0, fixture.modelCalls);
    }

    /** 后续POST压缩Hook结束Turn时保留此前Hook声明的持久化追加 */
    @Test
    void persistsEarlierPostCompressionAppendsBeforeLaterHookEndsTurn() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.compact = true;
        fixture.hooks.put("append", postCompressionAppendHook("append", "Hook补充"));
        fixture.hooks.put(
                "end",
                new LifecycleHook<
                        PostMessageCompressionContext, PostMessageCompressionHookResult>() {
                    @Override
                    public String name() {
                        return "postCompressionEnd";
                    }

                    @Override
                    public HookTypeDescriptor descriptor() {
                        return new HookTypeDescriptor(
                                HookPoint.POST_MESSAGE_COMPRESSION,
                                PostMessageCompressionContext.class,
                                PostMessageCompressionHookResult.class);
                    }

                    @Override
                    public PostMessageCompressionHookResult apply(
                            PostMessageCompressionContext context) {
                        return new EndTurnPostMessageCompression("结束");
                    }
                });
        fixture.definition =
                fixture.definition(
                        Map.of(
                                HookPoint.POST_MESSAGE_COMPRESSION,
                                List.of(
                                        new HookBinding(
                                                "append", "append", 0, true, List.of(), Map.of()),
                                        new HookBinding(
                                                "end", "end", 10, true, List.of(), Map.of()))),
                        Set.of(),
                        Set.of());

        AgentRunOutcome outcome = create(fixture).run();

        assertInstanceOf(AgentRunOutcome.EndedByHook.class, outcome);
        assertNotNull(fixture.compactionCommit);
        assertTrue(
                fixture.conversation.stream()
                        .anyMatch(message -> "Hook补充".equals(message.content())));
        assertEquals(0, fixture.modelCalls);
    }

    /** POST压缩Hook不能替换为其他压缩请求的结果 */
    @Test
    void rejectsPostCompressionResultWithDifferentCompactionId() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.compact = true;
        fixture.hooks.put(
                "replace-result",
                new LifecycleHook<
                        PostMessageCompressionContext, PostMessageCompressionHookResult>() {
                    @Override
                    public String name() {
                        return "replaceCompressionResult";
                    }

                    @Override
                    public HookTypeDescriptor descriptor() {
                        return new HookTypeDescriptor(
                                HookPoint.POST_MESSAGE_COMPRESSION,
                                PostMessageCompressionContext.class,
                                PostMessageCompressionHookResult.class);
                    }

                    @Override
                    public PostMessageCompressionHookResult apply(
                            PostMessageCompressionContext context) {
                        return new ContinuePostMessageCompression(
                                HookMutations.none(),
                                new ConversationCompactionResultPatch(
                                        new org.gemo.apex.common.conversation
                                                .ConversationCompactionResult(
                                                "other-compaction",
                                                context.result().summary(),
                                                context.result().retainedMessages(),
                                                context.result().metadata())));
                    }
                });
        fixture.definition =
                fixture.definition(
                        Map.of(
                                HookPoint.POST_MESSAGE_COMPRESSION,
                                List.of(
                                        new HookBinding(
                                                "replace-result",
                                                "replace-result",
                                                0,
                                                true,
                                                List.of(),
                                                Map.of()))),
                        Set.of(),
                        Set.of());

        AgentRunOutcome outcome = create(fixture).run();

        assertInstanceOf(AgentRunOutcome.Failed.class, outcome);
        assertNull(fixture.compactionCommit);
        assertEquals(0, fixture.modelCalls);
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

    /** 模型失败重试三次后三层失败并发送错误事件 */
    @Test
    void retriesThenFailsAtThreeLevelsAfterModelFailure() {
        CoreTestFixture fixture = new CoreTestFixture();
        fixture.modelFailure = new IllegalStateException("model down");
        ApexAgent agent = create(fixture);

        AgentRunOutcome outcome = agent.run();

        assertInstanceOf(AgentRunOutcome.Failed.class, outcome);
        assertEquals(4, fixture.modelCalls);
        assertEquals(SessionStatus.FAILED, agent.snapshot().status());
        assertEquals(TurnStatus.FAILED, agent.snapshot().activeTurn().status());
        assertEquals(
                IterationStatus.FAILED, agent.snapshot().activeTurn().currentIteration().status());
        assertEquals(2, fixture.events.size());
        TaskErrorMessage taskError =
                assertInstanceOf(TaskErrorMessage.class, fixture.events.getFirst());
        assertEquals("model down", taskError.getMessages().getFirst().getMessage());
        assertInstanceOf(EndMessage.class, fixture.events.getLast());
        assertEquals(1, agent.snapshot().executionErrors().size());
        assertEquals(
                ExecutionErrorType.MODEL, agent.snapshot().executionErrors().getFirst().type());
    }

    /** prepared取消不创建Iteration或调用模型工具 */
    @Test
    void preparedCancellationDoesNotCreateIterationOrInvokeModelOrTool() {
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
                    public String name() {
                        return "disableSecond";
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
        assertEquals(
                1,
                fixture.events.stream()
                        .filter(InvocationDeclaredMessage.class::isInstance)
                        .count());
        assertTrue(toolCallPayload(fixture.conversation, "c1").containsKey("resolvedArguments"));
        assertTrue(toolCallPayload(fixture.conversation, "c2").containsKey("resolvedArguments"));
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
                "工具执行失败：工具只能发布 INVOCATION_DECLARED/INVOCATION_CHANGE",
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
            public String name() {
                return "skillActivation";
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
                String skillName = (String) context.toolCall().arguments().get("command");
                return new ContinuePostToolCall(
                        HookMutations.none(),
                        new ToolResultPatch(
                                context.toolResult().content(), context.toolResult().metadata()),
                        new SkillActivationDelta(Set.of(skillName), Set.of()));
            }
        };
    }

    private LifecycleHook<PostToolCallContext, PostToolCallHookResult> postToolHook(
            Function<PostToolCallContext, ToolResultPatch> action) {
        return new LifecycleHook<>() {
            @Override
            public String name() {
                return "postTool";
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
                return new ContinuePostToolCall(HookMutations.none(), action.apply(context));
            }
        };
    }

    private LifecycleHook<PostMessageCompressionContext, PostMessageCompressionHookResult>
            postCompressionAppendHook(String operationId, String content) {
        return new LifecycleHook<>() {
            @Override
            public String name() {
                return "postCompressionAppend";
            }

            @Override
            public HookTypeDescriptor descriptor() {
                return new HookTypeDescriptor(
                        HookPoint.POST_MESSAGE_COMPRESSION,
                        PostMessageCompressionContext.class,
                        PostMessageCompressionHookResult.class);
            }

            @Override
            public PostMessageCompressionHookResult apply(PostMessageCompressionContext context) {
                return new ContinuePostMessageCompression(
                        new HookMutations(
                                List.of(
                                        new AppendMessage(
                                                operationId,
                                                MessageRole.SYSTEM,
                                                MessageType.TEXT,
                                                content,
                                                Map.of())),
                                ToolActivationDelta.none()),
                        new ConversationCompactionResultPatch(context.result()));
            }
        };
    }

    private LifecycleHook<AgentBuildContext, AgentBuildHookResult> buildHook(
            java.util.function.Function<AgentBuildContext, List<AgentDefinitionOperation>> action) {
        return new LifecycleHook<>() {
            @Override
            public String name() {
                return "agentBuild";
            }

            @Override
            public HookTypeDescriptor descriptor() {
                return new HookTypeDescriptor(
                        HookPoint.AGENT_BUILD, AgentBuildContext.class, AgentBuildHookResult.class);
            }

            @Override
            public AgentBuildHookResult apply(AgentBuildContext context) {
                return new ContinueAgentBuild(action.apply(context));
            }
        };
    }

    private LifecycleHook<PreToolCallContext, PreToolCallHookResult> preToolHook(
            Function<PreToolCallContext, PreToolCallHookResult> action) {
        return new LifecycleHook<>() {
            @Override
            public String name() {
                return "preTool";
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

    private Map<?, ?> toolCallPayload(
            List<org.gemo.apex.common.message.AgentMessageEntry> messages, String toolCallId) {
        for (var message : messages) {
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
