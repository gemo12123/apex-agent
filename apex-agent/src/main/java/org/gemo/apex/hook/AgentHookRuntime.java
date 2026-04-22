package org.gemo.apex.hook;

import org.gemo.apex.hook.tool.PostToolCallHookContext;
import org.gemo.apex.hook.tool.PostToolCallHookResult;
import org.gemo.apex.hook.tool.PreToolCallHookContext;
import org.gemo.apex.hook.tool.PreToolCallHookResult;

public interface AgentHookRuntime {

    PreToolCallHookResult runPreHooks(PreToolCallHookContext context);

    PostToolCallHookResult runPostHooks(PostToolCallHookContext context);
}
