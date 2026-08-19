package org.gemo.apex.common.hook.result;

public sealed interface PostModelCallHookResult extends LifecycleHookResult
        permits ContinuePostModelCall, EndTurnPostModelCall {}
