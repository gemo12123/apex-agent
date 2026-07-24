package org.gemo.apex.hook.lifecycle;

import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.config.model.AgentHooksConfig;
import org.gemo.apex.config.model.HookBindingConfig;
import org.gemo.apex.hook.ToolMatcher;
import org.gemo.apex.hook.tool.PostToolCallHook;
import org.gemo.apex.hook.tool.PostToolCallHookContext;
import org.gemo.apex.hook.tool.PostToolCallHookResult;
import org.gemo.apex.hook.tool.PreToolCallHook;
import org.gemo.apex.hook.tool.PreToolCallHookContext;
import org.gemo.apex.hook.tool.PreToolCallHookResult;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Component
public class DefaultAgentLifecycleHookRuntime implements AgentLifecycleHookRuntime {

    private final ApplicationContext applicationContext;
    private final ToolMatcher toolMatcher;

    public DefaultAgentLifecycleHookRuntime(ApplicationContext applicationContext, ToolMatcher toolMatcher) {
        this.applicationContext = applicationContext;
        this.toolMatcher = toolMatcher;
    }

    @Override
    public HookDispatchResult run(HookPoint point, AgentRuntimeContext context, Set<String> skippedHookBeans) {
        AgentHooksConfig hooks = context.getAgentDefinition() != null ? context.getAgentDefinition().hooks() : null;
        if (hooks == null || hooks.isDisabled()) {
            return HookDispatchResult.continued();
        }

        List<String> executed = new ArrayList<>();
        AgentHookResult lastResult = AgentHookResult.continueFlow();
        for (HookBindingConfig binding : matchingBindings(hooks, point, context)) {
            if (skippedHookBeans != null && skippedHookBeans.contains(binding.getBean())) {
                continue;
            }

            LocalDateTime startedAt = LocalDateTime.now();
            HookExecutionRecord record = HookExecutionRecord.builder()
                    .hookPoint(point)
                    .hookBean(binding.getBean())
                    .order(binding.getOrder())
                    .startedAt(startedAt)
                    .build();
            try {
                AgentHookResult result = invoke(point, context, binding);
                lastResult = result != null ? result : AgentHookResult.continueFlow();
                validateAction(point, lastResult.getAction());
                applyResult(point, context, binding.getBean(), lastResult);
                executed.add(binding.getBean());
                record.setAction(lastResult.getAction());
                record.setSucceeded(true);
                record.setEndedAt(LocalDateTime.now());
                addExecutionRecord(context, record);
                persistBoundary(context);
                if (lastResult.getAction() != HookFlowAction.CONTINUE) {
                    return HookDispatchResult.builder()
                            .result(lastResult)
                            .executedHookBeans(List.copyOf(executed))
                            .build();
                }
            } catch (Exception ex) {
                record.setAction(HookFlowAction.CONTINUE);
                record.setSucceeded(false);
                record.setError(ex.getMessage());
                record.setEndedAt(LocalDateTime.now());
                addExecutionRecord(context, record);
                persistBoundary(context);
                log.warn("生命周期 Hook 执行失败，已忽略并继续主流程: point={}, bean={}, agentKey={}, sessionId={}, turnNo={}, traceNo={}",
                        point,
                        binding.getBean(),
                        context.getSessionContext() != null ? context.getSessionContext().getAgentKey() : null,
                        context.getSessionContext() != null ? context.getSessionContext().getSessionId() : null,
                        context.getTurn() != null ? context.getTurn().getTurnNo() : null,
                        context.getTrace() != null ? context.getTrace().getTraceNo() : null,
                        ex);
            }
        }
        return HookDispatchResult.builder()
                .result(lastResult)
                .executedHookBeans(List.copyOf(executed))
                .build();
    }

    private AgentHookResult invoke(HookPoint point, AgentRuntimeContext context, HookBindingConfig binding) {
        Object bean = applicationContext.getBean(binding.getBean());
        if (bean instanceof AgentLifecycleHook lifecycleHook) {
            return lifecycleHook.apply(AgentHookContext.builder()
                    .hookPoint(point)
                    .runtimeContext(context)
                    .hookBean(binding.getBean())
                    .order(binding.getOrder())
                    .hookOptions(binding.getOptions())
                    .build());
        }
        if (point == HookPoint.PRE_TOOL_CALL && bean instanceof PreToolCallHook hook) {
            return adaptPreToolResult(hook.apply(buildPreToolContext(context, binding)));
        }
        if (point == HookPoint.POST_TOOL_CALL && bean instanceof PostToolCallHook hook) {
            return adaptPostToolResult(hook.apply(buildPostToolContext(context, binding)));
        }
        throw new IllegalArgumentException("Hook Bean 未实现适用接口: " + binding.getBean() + ", point=" + point);
    }

    private AgentHookResult adaptPreToolResult(PreToolCallHookResult result) {
        if (result == null) {
            return AgentHookResult.continueFlow();
        }
        return switch (result.getOutcome()) {
            case PROCEED -> result.getUpdatedArgs() != null
                    ? AgentHookResult.continueWithToolArguments(result.getUpdatedArgs())
                    : AgentHookResult.continueFlow();
            case BLOCK -> AgentHookResult.blockTool(result.getBlockReason());
            case REQUEST_CONFIRMATION -> AgentHookResult.requestConfirmation(
                    result.getConfirmationSpec(),
                    result.getUpdatedArgs());
        };
    }

    private AgentHookResult adaptPostToolResult(PostToolCallHookResult result) {
        if (result == null || result.getOutcome() == PostToolCallHookResult.Outcome.KEEP) {
            return AgentHookResult.continueFlow();
        }
        return AgentHookResult.replaceToolResult(result.getNextResult());
    }

    private PreToolCallHookContext buildPreToolContext(AgentRuntimeContext runtime, HookBindingConfig binding) {
        var session = runtime.getSessionContext();
        var call = runtime.getCurrentToolCall();
        return PreToolCallHookContext.builder()
                .agentKey(session.getAgentKey())
                .sessionId(session.getSessionId())
                .userId(session.getUserId())
                .toolCallId(call != null ? call.id() : null)
                .toolName(call != null ? call.name() : null)
                .rawArguments(call != null ? call.arguments() : null)
                .arguments(new LinkedHashMap<>(runtime.getCurrentToolArguments()))
                .hookOptions(binding.getOptions())
                .superAgentContext(session)
                .build();
    }

    private PostToolCallHookContext buildPostToolContext(AgentRuntimeContext runtime, HookBindingConfig binding) {
        var session = runtime.getSessionContext();
        var call = runtime.getCurrentToolCall();
        return PostToolCallHookContext.builder()
                .agentKey(session.getAgentKey())
                .sessionId(session.getSessionId())
                .userId(session.getUserId())
                .toolCallId(call != null ? call.id() : null)
                .toolName(call != null ? call.name() : null)
                .rawArguments(call != null ? call.arguments() : null)
                .arguments(new LinkedHashMap<>(runtime.getCurrentToolArguments()))
                .hookOptions(binding.getOptions())
                .hookSource(binding.getBean())
                .originalResult(runtime.getCurrentToolOriginalResult())
                .currentResult(runtime.getCurrentToolResult())
                .toolExecutionSucceeded(true)
                .superAgentContext(session)
                .build();
    }

    private void applyResult(HookPoint point, AgentRuntimeContext context, String hookBean, AgentHookResult result) {
        if (result.getUpdatedToolArguments() != null) {
            context.setCurrentToolArguments(new LinkedHashMap<>(result.getUpdatedToolArguments()));
        }
        if (result.getUpdatedToolResult() != null) {
            context.setCurrentToolResult(result.getUpdatedToolResult());
        }
        RuntimeException operationFailure = null;
        for (MessageOperation operation : result.getMessageOperations()) {
            try {
                applyMessageOperation(point, context, hookBean, operation);
            } catch (RuntimeException ex) {
                if (operationFailure == null) {
                    operationFailure = ex;
                }
            }
        }
        if (operationFailure != null) {
            throw operationFailure;
        }
    }

    private void applyMessageOperation(HookPoint point, AgentRuntimeContext context, String hookBean,
            MessageOperation operation) {
        MessageMutationRecord record = MessageMutationRecord.builder()
                .hookPoint(point)
                .hookBean(hookBean)
                .operation(operation != null ? operation.getType() : null)
                .index(operation != null ? operation.getIndex() : null)
                .build();
        try {
            if (operation == null || operation.getType() == null) {
                throw new IllegalArgumentException("MessageOperation 不能为空");
            }
            List<org.springframework.ai.chat.messages.Message> messages = context.getWorkingMessages();
            switch (operation.getType()) {
                case APPEND -> {
                    if (operation.getMessage() == null) {
                        throw new IllegalArgumentException("APPEND 必须提供 message");
                    }
                    messages.add(operation.getMessage());
                    record.setIndex(messages.size() - 1);
                    record.setAfterMessage(operation.getMessage());
                }
                case DELETE -> {
                    validateIndex(messages, operation.getIndex());
                    record.setBeforeMessage(messages.remove(operation.getIndex().intValue()));
                }
                case REPLACE -> {
                    validateIndex(messages, operation.getIndex());
                    if (operation.getMessage() == null) {
                        throw new IllegalArgumentException("REPLACE 必须提供 message");
                    }
                    record.setBeforeMessage(messages.set(operation.getIndex(), operation.getMessage()));
                    record.setAfterMessage(operation.getMessage());
                }
            }
            record.setApplied(true);
        } catch (Exception ex) {
            record.setApplied(false);
            record.setError(ex.getMessage());
            throw ex;
        } finally {
            if (context.getTrace() != null) {
                context.getTrace().getMessageMutations().add(record);
            } else if (context.getTurn() != null) {
                context.getTurn().getMessageMutations().add(record);
            }
        }
    }

    private void validateIndex(List<?> messages, Integer index) {
        if (index == null || index < 0 || index >= messages.size()) {
            throw new IndexOutOfBoundsException("消息索引越界: " + index + ", size=" + messages.size());
        }
    }

    private void addExecutionRecord(AgentRuntimeContext context, HookExecutionRecord record) {
        if (record.getHookPoint() == HookPoint.TURN_START || record.getHookPoint() == HookPoint.TURN_END) {
            if (context.getTurn() != null) {
                context.getTurn().getHookExecutions().add(record);
            }
        } else if (context.getTrace() != null) {
            context.getTrace().getHookExecutions().add(record);
        }
    }

    private List<HookBindingConfig> matchingBindings(AgentHooksConfig hooks, HookPoint point,
            AgentRuntimeContext context) {
        List<HookBindingConfig> bindings = switch (point) {
            case TURN_START -> hooks.getTurnStart();
            case TRACE_START -> hooks.getTraceStart();
            case PRE_MODEL_CALL -> hooks.getPreModelCall();
            case POST_MODEL_CALL -> hooks.getPostModelCall();
            case PRE_TOOL_CALL -> hooks.getPreToolCall();
            case POST_TOOL_CALL -> hooks.getPostToolCall();
            case TRACE_END -> hooks.getTraceEnd();
            case TURN_END -> hooks.getTurnEnd();
        };
        if (bindings == null || bindings.isEmpty()) {
            return List.of();
        }
        String toolName = context.getCurrentToolCall() != null ? context.getCurrentToolCall().name() : null;
        return bindings.stream()
                .filter(Objects::nonNull)
                .filter(HookBindingConfig::isEnabled)
                .filter(binding -> binding.getBean() != null && !binding.getBean().isBlank())
                .filter(binding -> !isToolPoint(point) || toolMatcher.matches(binding.getTools(), toolName))
                .sorted(Comparator.comparingInt(HookBindingConfig::getOrder))
                .toList();
    }

    private boolean isToolPoint(HookPoint point) {
        return point == HookPoint.PRE_TOOL_CALL || point == HookPoint.POST_TOOL_CALL;
    }

    private void persistBoundary(AgentRuntimeContext context) {
        if (context.getExecutionStore() == null) {
            return;
        }
        try {
            if (context.getTrace() != null) {
                context.getExecutionStore().saveTrace(context.getTrace());
            }
            if (context.getTurn() != null) {
                context.getExecutionStore().saveTurn(context.getTurn());
            }
        } catch (RuntimeException ex) {
            log.warn("Hook 边界 Trace 持久化失败，主流程继续: turnNo={}, traceNo={}",
                    context.getTurn() != null ? context.getTurn().getTurnNo() : null,
                    context.getTrace() != null ? context.getTrace().getTraceNo() : null,
                    ex);
        }
    }

    private void validateAction(HookPoint point, HookFlowAction action) {
        HookFlowAction resolved = action != null ? action : HookFlowAction.CONTINUE;
        if (resolved == HookFlowAction.CONTINUE) {
            return;
        }
        if (!isToolPoint(point)) {
            throw new IllegalArgumentException("只有工具前后 Hook 可以控制流程: point=" + point + ", action=" + resolved);
        }
        if ((resolved == HookFlowAction.BLOCK_TOOL || resolved == HookFlowAction.REQUEST_CONFIRMATION)
                && point != HookPoint.PRE_TOOL_CALL) {
            throw new IllegalArgumentException("该流程动作只允许工具前 Hook 使用: action=" + resolved);
        }
    }
}
