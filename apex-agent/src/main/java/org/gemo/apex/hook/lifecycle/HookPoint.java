package org.gemo.apex.hook.lifecycle;

public enum HookPoint {
    TURN_START,
    TRACE_START,
    PRE_MODEL_CALL,
    POST_MODEL_CALL,
    PRE_TOOL_CALL,
    POST_TOOL_CALL,
    TRACE_END,
    TURN_END
}
