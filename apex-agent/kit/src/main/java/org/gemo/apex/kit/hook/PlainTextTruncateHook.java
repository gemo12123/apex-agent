package org.gemo.apex.kit.hook;

import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.common.hook.HookTypeDescriptor;
import org.gemo.apex.common.hook.context.PostToolCallContext;
import org.gemo.apex.common.hook.operation.HookMutations;
import org.gemo.apex.common.hook.operation.ToolResultPatch;
import org.gemo.apex.common.hook.result.ContinuePostToolCall;
import org.gemo.apex.common.hook.result.PostToolCallHookResult;
import org.gemo.apex.common.message.MessageType;
import org.gemo.apex.extension.hook.LifecycleHook;

public final class PlainTextTruncateHook
        implements LifecycleHook<PostToolCallContext, PostToolCallHookResult> {
    public static final String REGISTRATION_NAME = "plainTextTruncateHook";
    public static final String MESSAGE_TYPE_METADATA_KEY = "messageType";
    public static final int DEFAULT_MAX_LENGTH = 4000;
    private static final String TRUNCATION_MARKER = "…";
    private static final HookTypeDescriptor DESCRIPTOR = new HookTypeDescriptor(HookPoint.POST_TOOL_CALL,
            PostToolCallContext.class, PostToolCallHookResult.class);
    private final int maxLength;

    public PlainTextTruncateHook() {
        this(DEFAULT_MAX_LENGTH);
    }

    public PlainTextTruncateHook(int maxLength) {
        if (maxLength <= 0) throw new IllegalArgumentException("maxLength 必须大于 0");
        this.maxLength = maxLength;
    }

    @Override
    public HookTypeDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public PostToolCallHookResult apply(PostToolCallContext context) {
        String content = context.toolResult().content();
        if (!isText(context) || content.codePointCount(0, content.length()) <= maxLength) {
            return keep(context);
        }
        int contentLength = Math.max(0, maxLength - TRUNCATION_MARKER.codePointCount(0, TRUNCATION_MARKER.length()));
        int end = content.offsetByCodePoints(0, contentLength);
        return new ContinuePostToolCall(HookMutations.none(),
                new ToolResultPatch(content.substring(0, end) + TRUNCATION_MARKER,
                        context.toolResult().metadata()));
    }

    private boolean isText(PostToolCallContext context) {
        Object rawType = context.toolResult().metadata().get(MESSAGE_TYPE_METADATA_KEY);
        if (rawType == null) return true;
        return rawType == MessageType.TEXT || MessageType.TEXT.name().equalsIgnoreCase(String.valueOf(rawType));
    }

    private ContinuePostToolCall keep(PostToolCallContext context) {
        return new ContinuePostToolCall(HookMutations.none(),
                new ToolResultPatch(context.toolResult().content(), context.toolResult().metadata()));
    }
}
