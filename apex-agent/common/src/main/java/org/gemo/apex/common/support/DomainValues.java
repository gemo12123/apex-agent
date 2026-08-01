package org.gemo.apex.common.support;

import org.gemo.apex.common.json.JsonUtils;
import org.gemo.apex.common.tool.CancellationRegistration;
import org.gemo.apex.common.tool.CancellationToken;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DomainValues {
    private DomainValues() {
    }

    public static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value;
    }

    public static <T> T nonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value;
    }

    public static int nonNegative(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " 不能小于 0");
        }
        return value;
    }

    public static long nonNegative(long value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " 不能小于 0");
        }
        return value;
    }

    public static <T> List<T> immutableList(List<T> values, String field) {
        nonNull(values, field);
        return List.copyOf(values);
    }

    public static Set<String> immutableNames(Set<String> values, String field) {
        nonNull(values, field);
        LinkedHashSet<String> copy = new LinkedHashSet<>();
        for (String value : values) {
            if (!copy.add(required(value, field))) {
                throw new IllegalArgumentException(field + " 包含重复名称: " + value);
            }
        }
        return Collections.unmodifiableSet(copy);
    }

    public static Map<String, Object> immutableMap(Map<String, Object> values, String field) {
        nonNull(values, field);
        Map<String, Object> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> copy.put(required(key, field + " key"), freeze(value, field)));
        return Collections.unmodifiableMap(copy);
    }

    public static Map<String, Object> jsonMap(Map<String, Object> values, String field) {
        Map<String, Object> copy = immutableMap(values, field);
        JsonUtils.toTree(copy);
        return copy;
    }

    public static Object immutableValue(Object value, String field) {
        return freeze(value, field);
    }

    @SuppressWarnings("unchecked")
    private static Object freeze(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (value instanceof CancellationToken || value instanceof CancellationRegistration
                || value instanceof Runnable) {
            throw new IllegalArgumentException(field + " 不允许包含取消 token、registration 或 command");
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, nested) -> {
                if (!(key instanceof String stringKey)) {
                    throw new IllegalArgumentException(field + " 只允许字符串 Map key");
                }
                copy.put(stringKey, freeze(nested, field));
            });
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(item -> copy.add(freeze(item, field)));
            return Collections.unmodifiableList(copy);
        }
        if (value instanceof Set<?> set) {
            Set<Object> copy = new LinkedHashSet<>();
            set.forEach(item -> copy.add(freeze(item, field)));
            return Collections.unmodifiableSet(copy);
        }
        return value;
    }
}
