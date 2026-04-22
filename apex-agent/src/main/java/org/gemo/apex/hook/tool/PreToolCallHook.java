package org.gemo.apex.hook.tool;

public interface PreToolCallHook {

    PreToolCallHookResult apply(PreToolCallHookContext context);
}
