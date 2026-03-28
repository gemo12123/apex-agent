package org.gemo.apex.memory.persistence.repository;

import org.gemo.apex.memory.model.MemoryItem;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版记忆存储。
 */
@Component
public class InMemoryMemoryStore {

    private final Map<String, MemoryItem> profileItems = new ConcurrentHashMap<>();
    private final Map<String, MemoryItem> executionHistoryItems = new ConcurrentHashMap<>();
    private final Map<String, MemoryItem> experienceItems = new ConcurrentHashMap<>();

    public Map<String, MemoryItem> getProfileItems() {
        return profileItems;
    }

    public Map<String, MemoryItem> getExecutionHistoryItems() {
        return executionHistoryItems;
    }

    public Map<String, MemoryItem> getExperienceItems() {
        return experienceItems;
    }
}
