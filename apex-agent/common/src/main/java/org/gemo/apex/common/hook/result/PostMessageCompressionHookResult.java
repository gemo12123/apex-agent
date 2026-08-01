package org.gemo.apex.common.hook.result;

public sealed interface PostMessageCompressionHookResult extends LifecycleHookResult
        permits ContinuePostMessageCompression, EndTurnPostMessageCompression {
}
