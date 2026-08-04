package org.gemo.apex.memory.persistence.repository;

import org.gemo.apex.memory.model.MemoryItem;
import org.gemo.apex.memory.model.MemoryQueryType;
import org.gemo.apex.memory.model.MemoryStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 内存版记忆管理仓储。
 */
@Component
public class InMemoryMemoryManageRepository implements MemoryManageRepository {

    private final InMemoryMemoryStore memoryStore;

    public InMemoryMemoryManageRepository(InMemoryMemoryStore memoryStore) {
        this.memoryStore = memoryStore;
    }

    @Override
    public List<MemoryItem> listItems(MemoryQueryType type, String userId, String agentKey, int page, int pageSize) {
        int offset = Math.max(0, page - 1) * pageSize;
        return selectMap(type).values().stream()
                .filter(item -> matches(type, item, userId, agentKey))
                .sorted(Comparator.comparing(MemoryItem::getObservedTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .skip(offset)
                .limit(pageSize)
                .toList();
    }

    @Override
    public Optional<MemoryItem> findItem(MemoryQueryType type, String memoryId, String userId, String agentKey) {
        MemoryItem item = selectMap(type).get(memoryId);
        if (item == null || !matches(type, item, userId, agentKey)) {
            return Optional.empty();
        }
        return Optional.of(item);
    }

    @Override
    public void updateContent(MemoryQueryType type, String memoryId, String userId, String agentKey, String content) {
        findItem(type, memoryId, userId, agentKey).ifPresent(item -> {
            item.setContent(content);
            item.setObservedTime(LocalDateTime.now());
        });
    }

    @Override
    public void deleteItem(MemoryQueryType type, String memoryId, String userId, String agentKey) {
        findItem(type, memoryId, userId, agentKey).ifPresent(item -> item.setStatus(MemoryStatus.DELETED));
    }

    @Override
    public void clearExecutionHistory(String userId, String agentKey) {
        memoryStore.getExecutionHistoryItems().values().stream()
                .filter(item -> userId.equals(item.getUserId()) && agentKey.equals(item.getAgentKey()))
                .forEach(item -> item.setStatus(MemoryStatus.DELETED));
    }

    private Map<String, MemoryItem> selectMap(MemoryQueryType type) {
        return switch (type) {
            case PROFILE -> memoryStore.getProfileItems();
            case EXECUTION_HISTORY -> memoryStore.getExecutionHistoryItems();
            case EXPERIENCE -> memoryStore.getExperienceItems();
        };
    }

    private boolean matches(MemoryQueryType type, MemoryItem item, String userId, String agentKey) {
        if (item.getStatus() == MemoryStatus.DELETED) {
            return false;
        }
        return switch (type) {
            case PROFILE, EXECUTION_HISTORY -> userId.equals(item.getUserId()) && agentKey.equals(item.getAgentKey());
            case EXPERIENCE -> agentKey.equals(item.getAgentKey());
        };
    }
}
