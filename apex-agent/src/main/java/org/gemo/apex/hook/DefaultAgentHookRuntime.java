package org.gemo.apex.hook;

import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.config.model.AgentHooksConfig;
import org.gemo.apex.config.model.HookBindingConfig;
import org.gemo.apex.definition.agent.IAgentDefinitionLoader;
import org.gemo.apex.hook.tool.PostToolCallHook;
import org.gemo.apex.hook.tool.PostToolCallHookContext;
import org.gemo.apex.hook.tool.PostToolCallHookResult;
import org.gemo.apex.hook.tool.PreToolCallHook;
import org.gemo.apex.hook.tool.PreToolCallHookContext;
import org.gemo.apex.hook.tool.PreToolCallHookResult;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Component
public class DefaultAgentHookRuntime implements AgentHookRuntime {

    private final IAgentDefinitionLoader agentDefinitionLoader;
    private final ApplicationContext applicationContext;
    private final ToolMatcher toolMatcher;

    public DefaultAgentHookRuntime(IAgentDefinitionLoader agentDefinitionLoader,
            ApplicationContext applicationContext,
            ToolMatcher toolMatcher) {
        this.agentDefinitionLoader = agentDefinitionLoader;
        this.applicationContext = applicationContext;
        this.toolMatcher = toolMatcher;
    }

    @Override
    public PreToolCallHookResult runPreHooks(PreToolCallHookContext context) {
        AgentHooksConfig hooks = agentDefinitionLoader.load(context.getAgentKey()).hooks();
        if (hooks == null || hooks.isDisabled()) {
            return PreToolCallHookResult.proceedWithUpdatedArgs(copyArguments(context));
        }

        LinkedHashMap<String, Object> currentArguments = copyArguments(context);
        Set<String> skippedHookBeans = context.getSkippedHookBeans() != null ? context.getSkippedHookBeans() : Set.of();
        List<String> executedHookBeans = new ArrayList<>();

        for (HookBindingConfig binding : matchingBindings(hooks.getPreToolCall(), context.getToolName())) {
            if (skippedHookBeans.contains(binding.getBean())) {
                continue;
            }

            PreToolCallHook hook = applicationContext.getBean(binding.getBean(), PreToolCallHook.class);
            PreToolCallHookResult result = hook.apply(context.toBuilder()
                    .arguments(new LinkedHashMap<>(currentArguments))
                    .hookOptions(binding.getOptions())
                    .build());

            if (result.getOutcome() == PreToolCallHookResult.Outcome.PROCEED) {
                if (result.getUpdatedArgs() != null) {
                    currentArguments.clear();
                    currentArguments.putAll(result.getUpdatedArgs());
                }
                executedHookBeans.add(binding.getBean());
                continue;
            }
            if (result.getOutcome() == PreToolCallHookResult.Outcome.REQUEST_CONFIRMATION
                    && result.getUpdatedArgs() == null) {
                List<String> progress = new ArrayList<>(executedHookBeans);
                progress.add(binding.getBean());
                return PreToolCallHookResult.builder()
                        .outcome(PreToolCallHookResult.Outcome.REQUEST_CONFIRMATION)
                        .updatedArgs(new LinkedHashMap<>(currentArguments))
                        .confirmationSpec(result.getConfirmationSpec())
                        .executedHookBeans(progress)
                        .build();
            }
            return result;
        }

        return PreToolCallHookResult.builder()
                .outcome(PreToolCallHookResult.Outcome.PROCEED)
                .updatedArgs(currentArguments)
                .executedHookBeans(List.copyOf(executedHookBeans))
                .build();
    }

    @Override
    public PostToolCallHookResult runPostHooks(PostToolCallHookContext context) {
        AgentHooksConfig hooks = agentDefinitionLoader.load(context.getAgentKey()).hooks();
        if (hooks == null || hooks.isDisabled()) {
            return PostToolCallHookResult.keep();
        }

        String currentResult = context.getCurrentResult();
        boolean replaced = false;

        for (HookBindingConfig binding : matchingBindings(hooks.getPostToolCall(), context.getToolName())) {
            try {
                PostToolCallHook hook = applicationContext.getBean(binding.getBean(), PostToolCallHook.class);
                PostToolCallHookResult result = hook.apply(context.toBuilder()
                        .hookOptions(binding.getOptions())
                        .hookSource(binding.getBean())
                        .currentResult(currentResult)
                        .build());
                if (result.getOutcome() == PostToolCallHookResult.Outcome.REPLACE_RESULT) {
                    currentResult = result.getNextResult();
                    replaced = true;
                }
            } catch (Exception ex) {
                log.warn("Post hook failed, agentKey={}, sessionId={}, toolCallId={}, invocationId={}, skillName={}, hookBean={}",
                        context.getAgentKey(),
                        context.getSessionId(),
                        context.getToolCallId(),
                        context.getInvocationId(),
                        context.getArguments() != null ? context.getArguments().get("command") : null,
                        binding.getBean(),
                        ex);
            }
        }

        return replaced ? PostToolCallHookResult.replaceResult(currentResult) : PostToolCallHookResult.keep();
    }

    private List<HookBindingConfig> matchingBindings(List<HookBindingConfig> bindings, String toolName) {
        if (bindings == null || bindings.isEmpty()) {
            return List.of();
        }

        return bindings.stream()
                .filter(Objects::nonNull)
                .filter(HookBindingConfig::isEnabled)
                .filter(binding -> toolMatcher.matches(binding.getTools(), toolName))
                .sorted(Comparator.comparingInt(HookBindingConfig::getOrder))
                .toList();
    }

    private LinkedHashMap<String, Object> copyArguments(PreToolCallHookContext context) {
        return context.getArguments() != null ? new LinkedHashMap<>(context.getArguments()) : new LinkedHashMap<>();
    }
}
