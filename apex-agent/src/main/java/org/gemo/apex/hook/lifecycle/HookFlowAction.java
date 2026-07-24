package org.gemo.apex.hook.lifecycle;

public enum HookFlowAction {
    CONTINUE,
    SKIP_TRACE,
    END_TURN,
    BLOCK_TOOL,
    REQUEST_CONFIRMATION
}
