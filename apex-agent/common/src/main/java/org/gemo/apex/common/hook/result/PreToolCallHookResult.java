package org.gemo.apex.common.hook.result;

public sealed interface PreToolCallHookResult extends LifecycleHookResult permits ContinuePreToolCall,
        BlockTool, ReturnToolResult, RequestHumanIntervention, EndTurnPreToolCall {
}
