package org.gemo.apex.common;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.gemo.apex.common.agent.AgentDefinitionOperation;
import org.gemo.apex.common.hook.HookBinding;
import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.common.hook.context.PreToolCallContext;
import org.gemo.apex.common.hook.operation.*;
import org.gemo.apex.common.hook.result.*;
import org.gemo.apex.common.intervention.QuestionSubmission;
import org.gemo.apex.common.message.MessageRole;
import org.gemo.apex.common.message.MessageType;
import org.gemo.apex.common.shared.SharedDataStores;
import org.gemo.apex.common.tool.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class HookContractTest {
    record Contract(HookPoint point, Class<? extends LifecycleHookResult> resultType) {}

    static Stream<Contract> contracts() {
        return Stream.of(
                new Contract(HookPoint.AGENT_BUILD, AgentBuildHookResult.class),
                new Contract(HookPoint.TURN_START, LoopHookResult.class),
                new Contract(HookPoint.ITERATION_START, LoopHookResult.class),
                new Contract(
                        HookPoint.PRE_MESSAGE_COMPRESSION, PreMessageCompressionHookResult.class),
                new Contract(
                        HookPoint.POST_MESSAGE_COMPRESSION, PostMessageCompressionHookResult.class),
                new Contract(HookPoint.PRE_MODEL_CALL, PreModelCallHookResult.class),
                new Contract(HookPoint.POST_MODEL_CALL, PostModelCallHookResult.class),
                new Contract(HookPoint.PRE_TOOL_CALL, PreToolCallHookResult.class),
                new Contract(HookPoint.POST_TOOL_CALL, PostToolCallHookResult.class),
                new Contract(HookPoint.ITERATION_END, LoopHookResult.class),
                new Contract(HookPoint.TURN_END, TurnEndHookResult.class));
    }

    /** 十一个生命周期均有明确结果族 */
    @ParameterizedTest
    @MethodSource("contracts")
    void allElevenLifecycleStagesHaveExplicitResultFamilies(Contract contract) {
        assertTrue(LifecycleHookResult.class.isAssignableFrom(contract.resultType()));
        assertNotEquals(LifecycleHookResult.class, contract.resultType());
    }

    /** turnEnd结果族只能Continue */
    @Test
    void turnEndResultFamilyAllowsOnlyContinue() {
        assertArrayEquals(
                new Class<?>[] {ContinueTurnEnd.class},
                TurnEndHookResult.class.getPermittedSubclasses());
        assertFalse(TurnEndHookResult.class.isAssignableFrom(EndTurnLoop.class));
    }

    /** 运行生命周期结果闭包不得出现Agent定义操作 */
    @Test
    void runtimeLifecycleResultClosureExcludesAgentDefinitionOperations() {
        List<Class<?>> resultRecords =
                List.of(
                        ContinueLoop.class,
                        EndTurnLoop.class,
                        ContinuePreMessageCompression.class,
                        EndTurnPreMessageCompression.class,
                        ContinuePostMessageCompression.class,
                        EndTurnPostMessageCompression.class,
                        ContinuePreModelCall.class,
                        EndTurnPreModelCall.class,
                        ContinuePostModelCall.class,
                        EndTurnPostModelCall.class,
                        ContinuePreToolCall.class,
                        BlockTool.class,
                        ReturnToolResult.class,
                        RequestHumanIntervention.class,
                        EndTurnPreToolCall.class,
                        ContinuePostToolCall.class,
                        EndTurnPostToolCall.class,
                        ContinueTurnEnd.class);

        for (Class<?> result : resultRecords) {
            for (RecordComponent component : result.getRecordComponents()) {
                assertFalse(
                        AgentDefinitionOperation.class.isAssignableFrom(component.getType()),
                        result.getName());
                assertFalse(
                        component.getGenericType().getTypeName().contains("AgentDefinition"),
                        result.getName());
                assertFalse(
                        component.getGenericType().getTypeName().contains("HookBinding"),
                        result.getName());
            }
        }
    }

    /** mutation构造时应拒绝重复操作和冲突工具变更 */
    @Test
    void mutationRejectsDuplicateOperationsAndConflictingToolChangesAtConstruction() {
        ToolResult result = new ToolResult("call", "tool", "done", Map.of());
        MessageOperation first = new AppendMessage("same", CommonFixtures.userMessage());
        MessageOperation second = new ReplaceMessage("same", 0, CommonFixtures.userMessage());
        assertThrows(
                IllegalArgumentException.class,
                () -> new HookMutations(List.of(first, second), ToolActivationDelta.none()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ToolActivationDelta(Set.of("tool"), Set.of("tool")));
        assertThrows(IllegalArgumentException.class, () -> new RemoveMessage("remove", -1));
        assertThrows(IllegalArgumentException.class, () -> new BlockTool(" "));
        assertThrows(IllegalArgumentException.class, () -> new ReturnToolResult(null));
    }

    /** 压缩后持久化追加拒绝SUMMARY与重复操作ID */
    @Test
    void postCompressionConversationAppendsRejectSummaryAndDuplicateOperationIds() {
        List<AppendConversationMessage> allowed =
                Stream.of(MessageType.values())
                        .filter(type -> type != MessageType.SUMMARY)
                        .map(
                                type ->
                                        new AppendConversationMessage(
                                                type.name(),
                                                MessageRole.SYSTEM,
                                                type,
                                                "补充",
                                                Map.of()))
                        .toList();
        assertEquals(3, allowed.size());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AppendConversationMessage(
                                "summary",
                                MessageRole.SYSTEM,
                                MessageType.SUMMARY,
                                "非法摘要",
                                Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ContinuePostMessageCompression(
                                HookMutations.none(),
                                new ConversationCompactionResultPatch(
                                        new org.gemo.apex.common.conversation
                                                .ConversationCompactionResult(
                                                "compaction",
                                                "摘要",
                                                List.of(),
                                                Map.of())),
                                List.of(allowed.getFirst(), allowed.getFirst())));
    }

    /** 工具调用上下文应暴露当前Binding与人工提交 */
    @Test
    void toolCallContextExposesCurrentBindingAndHumanSubmission() {
        HookBinding binding =
                new HookBinding(
                        "confirm",
                        "confirm",
                        10,
                        true,
                        List.of("search*"),
                        Map.of("title", "确认搜索"));
        QuestionSubmission submission = new QuestionSubmission("call-1", Map.of("0", "继续"));

        PreToolCallContext context =
                new PreToolCallContext(
                        "session-1",
                        binding,
                        CommonFixtures.toolCall(),
                        "invocation-1",
                        "confirmation-1",
                        submission,
                        SharedDataStores.create());

        assertEquals(binding, context.binding());
        assertEquals("确认搜索", context.binding().options().get("title"));
        assertEquals(submission, context.humanSubmission());
        assertThrows(
                IllegalArgumentException.class,
                () -> new HookBinding("invalid", "invalid", 0, true, List.of(" "), Map.of()));
    }

    /** 枚举中不存在SkipIteration */
    @Test
    void enumExcludesSkipIteration() {
        assertThrows(IllegalArgumentException.class, () -> HookPoint.valueOf("SKIP_ITERATION"));
    }
}
