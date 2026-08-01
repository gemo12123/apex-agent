package org.gemo.apex.hook.lifecycle;

public enum HookFlowAction {
    CONTINUE,
    SKIP_ITERATION,
    END_TURN,
    BLOCK_TOOL,
    REQUEST_CONFIRMATION
}
