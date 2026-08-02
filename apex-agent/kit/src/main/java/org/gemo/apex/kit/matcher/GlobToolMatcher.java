package org.gemo.apex.kit.matcher;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class GlobToolMatcher implements ToolMatcher {
    private final boolean matchAll;
    private final Set<String> exactNames;

    public GlobToolMatcher(List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            throw new IllegalArgumentException("patterns 不能为空");
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        boolean wildcard = false;
        for (String pattern : patterns) {
            if (pattern == null || pattern.isBlank()) {
                throw new IllegalArgumentException("pattern 不能为空");
            }
            if ("*".equals(pattern)) {
                wildcard = true;
            } else if (pattern.indexOf('*') >= 0) {
                throw new IllegalArgumentException("仅支持精确工具名或 *: " + pattern);
            } else if (!names.add(pattern)) {
                throw new IllegalArgumentException("pattern 重复: " + pattern);
            }
        }
        this.matchAll = wildcard;
        this.exactNames = Set.copyOf(names);
    }

    @Override
    public boolean matches(String toolName) {
        return toolName != null && (matchAll || exactNames.contains(toolName));
    }
}
