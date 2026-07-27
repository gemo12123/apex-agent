package org.gemo.apex.hook.lifecycle;

public enum HookPoint {
    TURN_START,
    ITERATION_START,
    PRE_MODEL_CALL,
    POST_MODEL_CALL,
    PRE_TOOL_CALL,
    POST_TOOL_CALL,
    ITERATION_END,
    TURN_END
}
