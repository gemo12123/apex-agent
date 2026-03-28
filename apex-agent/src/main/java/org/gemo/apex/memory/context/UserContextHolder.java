package org.gemo.apex.memory.context;

/**
 * 当前请求用户上下文持有器。
 */
public final class UserContextHolder {

    private static final ThreadLocal<String> USER_ID_HOLDER = new ThreadLocal<>();

    private UserContextHolder() {
    }

    public static void setUserId(String userId) {
        USER_ID_HOLDER.set(userId);
    }

    public static String getUserId() {
        return USER_ID_HOLDER.get();
    }

    public static void clear() {
        USER_ID_HOLDER.remove();
    }
}
