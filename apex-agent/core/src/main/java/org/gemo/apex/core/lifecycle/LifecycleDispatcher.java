package org.gemo.apex.core.lifecycle;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import org.gemo.apex.common.hook.HookBinding;
import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.common.hook.context.HookContextView;
import org.gemo.apex.common.hook.operation.*;
import org.gemo.apex.common.hook.result.*;
import org.gemo.apex.common.model.ModelRequest;
import org.gemo.apex.common.model.ModelResponse;
import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.common.tool.ToolResult;
import org.gemo.apex.core.agent.ApexAgentContext;
import org.gemo.apex.core.exception.HookContractException;
import org.gemo.apex.extension.hook.LifecycleHook;

/**
 * 生命周期 Hook 的统一分发器。
 *
 * <p>每次分发从当前定义取得按 {@code (order, id)} 排序的不可变 Binding 快照，保证 Hook 在 本轮执行期间不会因定义变更而改变顺序或集合。
 */
public final class LifecycleDispatcher {
    private static final System.Logger LOG = System.getLogger(LifecycleDispatcher.class.getName());
    private final ToolBindingMatcher toolMatcher = new ToolBindingMatcher();

    @SuppressWarnings({"rawtypes", "unchecked"})
    /** 分发非工具生命周期点，并在每个 Hook 返回后校验和应用允许的状态变更。 */
    public LifecycleDispatchOutcome dispatch(
            HookPoint point,
            ApexAgentContext context,
            BiFunction<ApexAgentContext, HookBinding, HookContextView> contextFactory,
            Set<String> skippedBindingIds) {
        return dispatch(
                point,
                context,
                contextFactory,
                skippedBindingIds,
                ignored -> {},
                (binding, result) -> new LifecycleDispatchOutcome.Continued());
    }

    /** 分发后允许可选子流程在每个 Continue 结果应用后重建自己的输入。 */
    public LifecycleDispatchOutcome dispatch(
            HookPoint point,
            ApexAgentContext context,
            BiFunction<ApexAgentContext, HookBinding, HookContextView> contextFactory,
            Set<String> skippedBindingIds,
            ContinuationObserver continuationObserver) {
        return dispatch(
                point,
                context,
                contextFactory,
                skippedBindingIds,
                ignored -> {},
                continuationObserver);
    }

    /** 分发单个 ToolCall 的 PRE_TOOL_CALL；工具匹配在调用前完成，结果可阻止或请求人工介入。 */
    public PreToolDispatchOutcome dispatchPreTool(
            ApexAgentContext context,
            BiFunction<ApexAgentContext, HookBinding, HookContextView> contextFactory,
            Collection<String> executedBindingIds) {
        List<String> completed = new ArrayList<>(executedBindingIds);
        LifecycleDispatchOutcome outcome =
                dispatch(
                        HookPoint.PRE_TOOL_CALL,
                        context,
                        contextFactory,
                        Set.copyOf(executedBindingIds),
                        binding -> completed.add(binding.id()),
                        (binding, result) -> new LifecycleDispatchOutcome.Continued());
        return new PreToolDispatchOutcome(outcome, completed);
    }

    private LifecycleDispatchOutcome dispatch(
            HookPoint point,
            ApexAgentContext context,
            BiFunction<ApexAgentContext, HookBinding, HookContextView> contextFactory,
            Set<String> skippedBindingIds,
            Consumer<HookBinding> completedBinding,
            ContinuationObserver continuationObserver) {
        if (point != HookPoint.PRE_TOOL_CALL && !skippedBindingIds.isEmpty()) {
            throw new HookContractException("只有 PRE_TOOL_CALL 恢复允许跳过 Hook");
        }
        List<HookBinding> bindings =
                context.definition().definition().hooks().getOrDefault(point, List.of()).stream()
                        .filter(HookBinding::enabled)
                        .filter(
                                binding ->
                                        !isToolPoint(point)
                                                || toolMatcher.matches(binding, context.toolCall()))
                        .filter(binding -> !skippedBindingIds.contains(binding.id()))
                        .sorted(
                                Comparator.comparingInt(HookBinding::order)
                                        .thenComparing(HookBinding::id))
                        .toList();
        for (HookBinding binding : bindings) {
            LifecycleHook hook = context.ports().hookResolver().resolve(point, binding.hook());
            if (hook == null
                    || hook.descriptor() == null
                    || hook.descriptor().hookPoint() != point) {
                throw new HookContractException("Hook 无法解析或 descriptor 不匹配: " + binding.id());
            }
            Object raw;
            try {
                HookContextView view = contextFactory.apply(context, binding);
                if (hook.descriptor().contextType() != view.getClass()) {
                    throw new HookContractException("Hook Context 类型不匹配: " + binding.id());
                }
                raw = hook.apply(view);
            } catch (HookContractException error) {
                throw error;
            } catch (RuntimeException error) {
                LOG.log(
                        System.Logger.Level.WARNING,
                        "生命周期 Hook 执行失败，已跳过: point=" + point + ", hookId=" + binding.id(),
                        error);
                context.recordHookFailure(point, binding.id());
                continue;
            }
            if (raw == null || !hook.descriptor().resultType().isInstance(raw)) {
                throw new HookContractException("Hook Result 类型不匹配: " + binding.id());
            }
            LifecycleDispatchOutcome outcome = validateAndApply(point, context, raw);
            completedBinding.accept(binding);
            if (!(outcome instanceof LifecycleDispatchOutcome.Continued)) {
                return outcome;
            }
            LifecycleDispatchOutcome continuation =
                    continuationObserver.afterContinue(binding, (LifecycleHookResult) raw);
            if (!(continuation instanceof LifecycleDispatchOutcome.Continued)) {
                return continuation;
            }
        }
        return new LifecycleDispatchOutcome.Continued();
    }

    private boolean isToolPoint(HookPoint point) {
        return point == HookPoint.PRE_TOOL_CALL || point == HookPoint.POST_TOOL_CALL;
    }

    /** 验证 Hook 只能返回与生命周期点匹配的结果类型，再将声明式 mutations 写回上下文。 */
    private LifecycleDispatchOutcome validateAndApply(
            HookPoint point, ApexAgentContext context, Object result) {
        if (point == HookPoint.TURN_END && !(result instanceof ContinueTurnEnd)) {
            throw new HookContractException("TURN_END 只允许 Continue");
        }
        return switch (result) {
            case ContinueLoop value -> {
                applyMutations(point, context, value.mutations());
                yield continued();
            }
            case EndTurnLoop value -> end(value.reason());
            case ContinuePreModelCall value -> {
                applyMutations(point, context, value.mutations());
                yield continued();
            }
            case EndTurnPreModelCall value -> end(value.reason());
            case ContinuePostModelCall value -> {
                validateResponseAssociation(context.modelResponse(), value.patch().replacement());
                applyMutations(point, context, value.mutations());
                context.modelResponse(value.patch().replacement());
                yield continued();
            }
            case EndTurnPostModelCall value -> end(value.reason());
            case ContinuePreToolCall value -> {
                applyMutations(point, context, value.mutations());
                ToolCall call = context.toolCall();
                context.toolCall(
                        new ToolCall(
                                call.toolCallId(),
                                call.name(),
                                call.ordinal(),
                                value.patch().arguments(),
                                call.metadata()));
                yield continued();
            }
            case BlockTool value -> new LifecycleDispatchOutcome.BlockTool(value.reason());
            case ReturnToolResult value -> {
                validateToolResult(context.toolCall(), value.result());
                yield new LifecycleDispatchOutcome.DirectToolResult(value.result());
            }
            case RequestHumanIntervention value ->
                    new LifecycleDispatchOutcome.HumanIntervention(value.request());
            case EndTurnPreToolCall value -> end(value.reason());
            case ContinuePostToolCall value -> {
                applyMutations(point, context, value.mutations());
                context.stageSkillActivation(value.skillActivationDelta());
                ToolResult old = context.toolResult();
                context.toolResult(
                        new ToolResult(
                                old.toolCallId(),
                                old.toolName(),
                                value.patch().content(),
                                value.patch().metadata()));
                yield continued();
            }
            case EndTurnPostToolCall value -> end(value.reason());
            case ContinuePreMessageCompression value -> {
                if (!value.mutations().messageOperations().isEmpty()
                        && !value.patch().replacement().equals(context.compactionRequest())) {
                    throw new HookContractException("PRE_MESSAGE_COMPRESSION 修改消息时不能同时替换压缩请求");
                }
                applyMutations(point, context, value.mutations());
                context.compactionRequest(value.patch().replacement());
                yield continued();
            }
            case EndTurnPreMessageCompression value -> end(value.reason());
            case ContinuePostMessageCompression value -> {
                applyToolActivation(context, value.mutations().toolActivationDelta());
                context.stagePostCompressionOperations(value.mutations().messageOperations());
                context.compactionResult(value.patch().replacement());
                yield continued();
            }
            case EndTurnPostMessageCompression value -> end(value.reason());
            case ContinueTurnEnd ignored -> continued();
            default ->
                    throw new HookContractException("生命周期结果族不受支持: " + result.getClass().getName());
        };
    }

    /** 将持久化消息操作和工具启停按生命周期语义应用。 */
    private void applyMutations(
            HookPoint point, ApexAgentContext context, HookMutations mutations) {
        applyToolActivation(context, mutations.toolActivationDelta());
        if (!mutations.messageOperations().isEmpty()) {
            context.mutateConversation(
                    mutations.messageOperations(),
                    point == HookPoint.PRE_TOOL_CALL || point == HookPoint.POST_TOOL_CALL);
        }
        if (point == HookPoint.PRE_MODEL_CALL) {
            synchronizePreModelRequest(context);
        }
    }

    private void applyToolActivation(
            ApexAgentContext context, ToolActivationDelta toolActivationDelta) {
        Set<String> nextEnabled = new LinkedHashSet<>(context.snapshot().enabledTools());
        if (!context.definition()
                .definition()
                .tools()
                .availableTools()
                .containsAll(toolActivationDelta.enable())) {
            throw new HookContractException("Hook 尝试启用不可用工具");
        }
        context.enableTools(toolActivationDelta.enable(), toolActivationDelta.disable());
    }

    private void synchronizePreModelRequest(ApexAgentContext context) {
        ModelRequest current = context.modelRequest();
        if (current == null) {
            throw new HookContractException("PRE_MODEL_CALL 缺少 ModelRequest");
        }
        var tools =
                context.toolCatalog().ordered().stream()
                        .filter(
                                tool ->
                                        context.snapshot()
                                                .enabledTools()
                                                .contains(tool.definition().name()))
                        .map(tool -> tool.definition())
                        .toList();
        context.modelRequest(
                new ModelRequest(
                        current.systemPrompt(),
                        current.prefixDeveloperMessages(),
                        context.conversationWindow().messages(),
                        tools,
                        current.options()));
    }

    private void validateResponseAssociation(ModelResponse before, ModelResponse after) {
        if (before == null || before.toolCalls().size() != after.toolCalls().size()) {
            throw new HookContractException("POST_MODEL 不能改变 ToolCall 数量");
        }
        for (int i = 0; i < before.toolCalls().size(); i++) {
            ToolCall left = before.toolCalls().get(i), right = after.toolCalls().get(i);
            if (!left.toolCallId().equals(right.toolCallId())
                    || !left.name().equals(right.name())) {
                throw new HookContractException("POST_MODEL 不能改变 ToolCall ID/name");
            }
        }
    }

    private void validateToolResult(ToolCall call, ToolResult result) {
        if (!call.toolCallId().equals(result.toolCallId())
                || !call.name().equals(result.toolName())) {
            throw new HookContractException("ToolResult 与 ToolCall 不关联");
        }
    }

    private LifecycleDispatchOutcome continued() {
        return new LifecycleDispatchOutcome.Continued();
    }

    private LifecycleDispatchOutcome end(String reason) {
        return new LifecycleDispatchOutcome.EndTurn(reason);
    }

    @FunctionalInterface
    public interface ContinuationObserver {
        LifecycleDispatchOutcome afterContinue(HookBinding binding, LifecycleHookResult result);
    }
}
