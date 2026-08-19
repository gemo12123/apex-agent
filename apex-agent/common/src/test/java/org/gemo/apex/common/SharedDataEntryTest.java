package org.gemo.apex.common;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.gemo.apex.common.shared.SharedDataCleanupPolicy;
import org.gemo.apex.common.shared.SharedDataEntry;
import org.gemo.apex.common.shared.SharedDataStore;
import org.gemo.apex.common.shared.SharedDataStores;
import org.junit.jupiter.api.Test;

class SharedDataEntryTest {
    @Test
    void supportsMapLikeOperationsAndPolicyChanges() {
        SharedDataStore store = SharedDataStores.create();

        store.put("key", Map.of("value", 1), SharedDataCleanupPolicy.ITERATION_END);
        assertTrue(store.containsKey("key"));
        assertEquals(Map.of("value", 1), store.get("key"));

        store.setCleanupPolicy("key", SharedDataCleanupPolicy.NEVER);
        assertEquals(SharedDataCleanupPolicy.NEVER, store.entries().get("key").cleanupPolicy());
        assertThrows(
                UnsupportedOperationException.class,
                () -> store.entries().put("other", store.entries().get("key")));
        assertEquals(SharedDataCleanupPolicy.NEVER, store.remove("key").cleanupPolicy());
        assertFalse(store.containsKey("key"));
        assertThrows(
                IllegalArgumentException.class,
                () -> store.setCleanupPolicy("missing", SharedDataCleanupPolicy.TURN_END));
        assertThrows(
                IllegalArgumentException.class,
                () -> store.put(" ", 1, SharedDataCleanupPolicy.NEVER));
    }

    @Test
    void normalizesValuesToImmutableJsonModel() {
        SharedDataEntry entry =
                new SharedDataEntry(
                        SharedDataCleanupPolicy.NEVER,
                        Map.of("nested", List.of(Map.of("value", 1))));

        Map<?, ?> value = assertInstanceOf(Map.class, entry.value());
        assertEquals(List.of(Map.of("value", 1)), value.get("nested"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> ((Map<Object, Object>) value).put("other", 2));
    }

    @Test
    void rejectsNonJsonValuesImmediately() {
        class SelfReference {
            public final SelfReference self = this;
        }

        assertThrows(
                RuntimeException.class,
                () -> new SharedDataEntry(SharedDataCleanupPolicy.NEVER, new SelfReference()));
    }
}
