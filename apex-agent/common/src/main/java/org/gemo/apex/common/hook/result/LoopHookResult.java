package org.gemo.apex.common.hook.result;

public sealed interface LoopHookResult extends LifecycleHookResult permits ContinueLoop, EndTurnLoop {
}
