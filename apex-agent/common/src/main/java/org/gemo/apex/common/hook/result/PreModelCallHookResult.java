package org.gemo.apex.common.hook.result;

public sealed interface PreModelCallHookResult extends LifecycleHookResult
        permits ContinuePreModelCall, EndTurnPreModelCall {}
