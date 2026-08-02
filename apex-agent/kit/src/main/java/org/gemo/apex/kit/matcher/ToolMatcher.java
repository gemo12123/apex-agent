package org.gemo.apex.kit.matcher;

@FunctionalInterface
public interface ToolMatcher {
    boolean matches(String toolName);
}
