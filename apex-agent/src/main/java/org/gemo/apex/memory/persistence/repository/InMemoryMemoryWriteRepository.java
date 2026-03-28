package org.gemo.apex.memory.persistence.repository;

import org.gemo.apex.memory.model.MemoryItem;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 内存版记忆写仓储。
 */
@Component
public class InMemoryMemoryWriteRepository implements MemoryWriteRepository {

    private final InMemoryMemoryStore memoryStore;

    public InMemoryMemoryWriteRepository(InMemoryMemoryStore memoryStore) {
        this.memoryStore = memoryStore;
    }

    @Override
    public void upsertProfileItem(MemoryItem item) {
        memoryStore.getProfileItems().entrySet().removeIf(entry -> Objects.equals(entry.getValue().getUserId(), item.getUserId())
                && Objects.equals(entry.getValue().getAgentKey(), item.getAgentKey())
                && Objects.equals(entry.getValue().getMemoryKey(), item.getMemoryKey()));
        memoryStore.getProfileItems().put(item.getId(), item);
    }

    @Override
    public void upsertExecutionHistoryItem(MemoryItem item) {
        memoryStore.getExecutionHistoryItems().entrySet().removeIf(entry -> Objects.equals(entry.getValue().getUserId(), item.getUserId())
                && Objects.equals(entry.getValue().getAgentKey(), item.getAgentKey())
                && Objects.equals(entry.getValue().getTopicKey(), item.getTopicKey())
                && Objects.equals(entry.getValue().getSourceSessionId(), item.getSourceSessionId())
                && Objects.equals(entry.getValue().getTimeScope(), item.getTimeScope()));
        memoryStore.getExecutionHistoryItems().put(item.getId(), item);
    }

    @Override
    public void upsertExperienceItem(MemoryItem item) {
        memoryStore.getExperienceItems().entrySet().removeIf(entry -> Objects.equals(entry.getValue().getAgentKey(), item.getAgentKey())
                && Objects.equals(entry.getValue().getTopicKey(), item.getTopicKey())
                && Objects.equals(entry.getValue().getMemoryKey(), item.getMemoryKey()));
        memoryStore.getExperienceItems().put(item.getId(), item);
    }
}
