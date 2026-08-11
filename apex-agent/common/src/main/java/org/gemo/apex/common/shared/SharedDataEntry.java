package org.gemo.apex.common.shared;

import static org.gemo.apex.common.support.DomainValues.immutableValue;
import static org.gemo.apex.common.support.DomainValues.nonNull;

import org.gemo.apex.common.json.JsonUtils;

/** 可持久化的共享数据条目。值在写入时归一化为 JSON 值模型。 */
public record SharedDataEntry(SharedDataCleanupPolicy cleanupPolicy, Object value) {
    public SharedDataEntry {
        cleanupPolicy = nonNull(cleanupPolicy, "cleanupPolicy");
        value = normalize(value);
    }

    private static Object normalize(Object value) {
        if (value == null) {
            return null;
        }
        Object frozen = immutableValue(value, "value");
        Object normalized = JsonUtils.fromJson(JsonUtils.toJson(frozen), Object.class);
        return immutableValue(normalized, "value");
    }
}
