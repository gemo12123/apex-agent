package org.gemo.apex.hook;

import org.gemo.apex.hook.tool.PostToolCallHookContext;
import org.gemo.apex.hook.tool.PostToolCallHookResult;
import org.gemo.apex.hook.tool.PreToolCallHookContext;
import org.gemo.apex.hook.tool.PreToolCallHookResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;

//@Component
public class NoOpAgentHookRuntime implements AgentHookRuntime {

    @Override
    public PreToolCallHookResult runPreHooks(PreToolCallHookContext context) {
        return PreToolCallHookResult.proceedWithUpdatedArgs(
                context.getArguments() != null ? new LinkedHashMap<>(context.getArguments()) : new LinkedHashMap<>());
    }

    @Override
    public PostToolCallHookResult runPostHooks(PostToolCallHookContext context) {
        return PostToolCallHookResult.keep();
    }
}
