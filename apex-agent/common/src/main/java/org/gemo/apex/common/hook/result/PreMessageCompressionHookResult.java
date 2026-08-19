package org.gemo.apex.common.hook.result;

public sealed interface PreMessageCompressionHookResult extends LifecycleHookResult
        permits ContinuePreMessageCompression, EndTurnPreMessageCompression {}
