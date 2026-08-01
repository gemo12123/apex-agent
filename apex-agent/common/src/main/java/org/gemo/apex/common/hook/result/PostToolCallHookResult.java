package org.gemo.apex.common.hook.result;

public sealed interface PostToolCallHookResult extends LifecycleHookResult permits ContinuePostToolCall, EndTurnPostToolCall {
}
