package org.gemo.apex.platform.security;

public final class UserContextHolder {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();
    private UserContextHolder() { }
    public static void set(String userId) { CURRENT.set(userId); }
    public static String get() { return CURRENT.get(); }
    public static void clear() { CURRENT.remove(); }
}
