package org.gemo.apex.common.hook.result;

public sealed interface LifecycleHookResult permits AgentBuildHookResult, LoopHookResult,
        PreMessageCompressionHookResult, PostMessageCompressionHookResult, PreModelCallHookResult,
        PostModelCallHookResult, PreToolCallHookResult, PostToolCallHookResult, TurnEndHookResult {
}
