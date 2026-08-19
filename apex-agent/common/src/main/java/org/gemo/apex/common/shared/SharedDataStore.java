package org.gemo.apex.common.shared;

import java.util.Map;

/** Hook 与 AgentTool 在同一 Session 中共享的同步键值存储。 */
public interface SharedDataStore {
    Object get(String key);

    boolean containsKey(String key);

    void put(String key, Object value, SharedDataCleanupPolicy cleanupPolicy);

    void setCleanupPolicy(String key, SharedDataCleanupPolicy cleanupPolicy);

    SharedDataEntry remove(String key);

    Map<String, SharedDataEntry> entries();
}
