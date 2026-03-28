package org.gemo.apex.memory.persistence.repository;

import org.gemo.apex.memory.model.MemoryItem;
import org.gemo.apex.memory.model.MemoryStatus;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 内存版记忆读仓储。
 */
@Component
public class InMemoryMemoryReadRepository implements MemoryReadRepository {

    private final InMemoryMemoryStore memoryStore;

    public InMemoryMemoryReadRepository(InMemoryMemoryStore memoryStore) {
        this.memoryStore = memoryStore;
    }

    @Override
    public List<MemoryItem> findProfileItems(String userId, String agentKey, int limit) {
        return memoryStore.getProfileItems().values().stream()
                .filter(item -> userId.equals(item.getUserId()) && agentKey.equals(item.getAgentKey()))
                .filter(item -> item.getStatus() == MemoryStatus.ACTIVE)
                .sorted(Comparator.comparing(MemoryItem::getObservedTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
    }

    @Override
    public List<MemoryItem> findExecutionHistoryItems(String userId, String agentKey, String excludeSessionId, int limit) {
        return memoryStore.getExecutionHistoryItems().values().stream()
                .filter(item -> userId.equals(item.getUserId()) && agentKey.equals(item.getAgentKey()))
                .filter(item -> item.getStatus() == MemoryStatus.ACTIVE)
                .filter(item -> excludeSessionId == null || !excludeSessionId.equals(item.getSourceSessionId()))
                .sorted(Comparator.comparing(MemoryItem::getObservedTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
    }

    @Override
    public List<MemoryItem> findExperienceItems(String agentKey, int limit) {
        return memoryStore.getExperienceItems().values().stream()
                .filter(item -> agentKey.equals(item.getAgentKey()))
                .filter(item -> item.getStatus() == MemoryStatus.ACTIVE)
                .sorted(Comparator.comparing(MemoryItem::getObservedTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
    }
}
