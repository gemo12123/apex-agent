package org.gemo.apex.hook;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ToolMatcher {

    public boolean matches(List<String> patterns, String toolName) {
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        return patterns.stream().anyMatch(pattern -> "*".equals(pattern) || pattern.equals(toolName));
    }
}
