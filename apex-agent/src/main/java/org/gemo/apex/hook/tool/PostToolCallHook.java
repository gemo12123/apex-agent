package org.gemo.apex.hook.tool;

public interface PostToolCallHook {

    PostToolCallHookResult apply(PostToolCallHookContext context);
}
