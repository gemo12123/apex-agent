package org.gemo.apex.hook.tool.builtin;

import org.gemo.apex.hook.tool.PostToolCallHook;
import org.gemo.apex.hook.tool.PostToolCallHookContext;
import org.gemo.apex.hook.tool.PostToolCallHookResult;
import org.springframework.stereotype.Component;

@Component("plainTextTruncateHook")
public class PlainTextTruncateHook implements PostToolCallHook {

    @Override
    public PostToolCallHookResult apply(PostToolCallHookContext context) {
        String current = context.getCurrentResult();
        if (current == null || current.isBlank() || looksLikeJson(current)) {
            return PostToolCallHookResult.keep();
        }

        int maxLength = intOption(context.getHookOptions() != null ? context.getHookOptions().get("max-length") : null,
                4000);
        if (current.length() <= maxLength) {
            return PostToolCallHookResult.keep();
        }

        String hookSource = context.getHookSource() != null ? context.getHookSource() : "unknown";
        String truncated = current.substring(0, maxLength)
                + "\n\n...[truncated by post-hook: "
                + hookSource
                + ", original_length="
                + current.length()
                + "]";
        return PostToolCallHookResult.replaceResult(truncated);
    }

    private boolean looksLikeJson(String value) {
        String trimmed = value.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }

    private int intOption(Object rawValue, int defaultValue) {
        if (rawValue == null) {
            return defaultValue;
        }
        if (rawValue instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(rawValue));
    }
}
