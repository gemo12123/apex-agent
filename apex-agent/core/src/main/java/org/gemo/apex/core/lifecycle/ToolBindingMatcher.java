package org.gemo.apex.core.lifecycle;

import org.gemo.apex.common.hook.HookBinding;
import org.gemo.apex.common.tool.ToolCall;

public final class ToolBindingMatcher {
    public boolean matches(HookBinding binding, ToolCall call) {
        return binding.tools().isEmpty()
                || call == null
                || binding.tools().stream().anyMatch(pattern -> matches(pattern, call.name()));
    }

    public boolean matches(String pattern, String toolName) {
        int patternIndex = 0;
        int toolIndex = 0;
        int wildcardIndex = -1;
        int wildcardToolIndex = -1;
        while (toolIndex < toolName.length()) {
            if (patternIndex < pattern.length()
                    && pattern.charAt(patternIndex) == toolName.charAt(toolIndex)) {
                patternIndex++;
                toolIndex++;
            } else if (patternIndex < pattern.length() && pattern.charAt(patternIndex) == '*') {
                wildcardIndex = patternIndex++;
                wildcardToolIndex = toolIndex;
            } else if (wildcardIndex >= 0) {
                patternIndex = wildcardIndex + 1;
                toolIndex = ++wildcardToolIndex;
            } else {
                return false;
            }
        }
        while (patternIndex < pattern.length() && pattern.charAt(patternIndex) == '*') {
            patternIndex++;
        }
        return patternIndex == pattern.length();
    }
}
