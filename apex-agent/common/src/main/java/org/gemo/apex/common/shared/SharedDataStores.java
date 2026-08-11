package org.gemo.apex.common.shared;

import static org.gemo.apex.common.support.DomainValues.nonNull;
import static org.gemo.apex.common.support.DomainValues.required;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** {@link SharedDataStore} 的标准同步实现工厂。 */
public final class SharedDataStores {
    private SharedDataStores() {}

    public static SharedDataStore create() {
        return create(Map.of());
    }

    public static SharedDataStore create(Map<String, SharedDataEntry> initialEntries) {
        return new StandardSharedDataStore(initialEntries);
    }

    private static final class StandardSharedDataStore implements SharedDataStore {
        private final Map<String, SharedDataEntry> entries = new LinkedHashMap<>();

        private StandardSharedDataStore(Map<String, SharedDataEntry> initialEntries) {
            nonNull(initialEntries, "initialEntries")
                    .forEach(
                            (key, value) ->
                                    entries.put(
                                            required(key, "sharedData key"),
                                            nonNull(value, "sharedData value")));
        }

        @Override
        public Object get(String key) {
            SharedDataEntry entry = entries.get(required(key, "key"));
            return entry == null ? null : entry.value();
        }

        @Override
        public boolean containsKey(String key) {
            return entries.containsKey(required(key, "key"));
        }

        @Override
        public void put(String key, Object value, SharedDataCleanupPolicy cleanupPolicy) {
            entries.put(
                    required(key, "key"),
                    new SharedDataEntry(nonNull(cleanupPolicy, "cleanupPolicy"), value));
        }

        @Override
        public void setCleanupPolicy(String key, SharedDataCleanupPolicy cleanupPolicy) {
            String validatedKey = required(key, "key");
            SharedDataEntry current = entries.get(validatedKey);
            if (current == null) {
                throw new IllegalArgumentException("共享数据不存在: " + validatedKey);
            }
            entries.put(
                    validatedKey,
                    new SharedDataEntry(nonNull(cleanupPolicy, "cleanupPolicy"), current.value()));
        }

        @Override
        public SharedDataEntry remove(String key) {
            return entries.remove(required(key, "key"));
        }

        @Override
        public Map<String, SharedDataEntry> entries() {
            return Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        }
    }
}
