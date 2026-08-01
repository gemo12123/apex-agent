package org.gemo.apex.memory.web.service;

import org.gemo.apex.memory.config.MemoryConfigService;
import org.gemo.apex.memory.model.MemoryItem;
import org.gemo.apex.memory.model.MemoryQueryType;
import org.gemo.apex.memory.persistence.repository.MemoryManageRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 记忆管理服务。
 */
@Service
public class MemoryManageService {

    private final MemoryManageRepository memoryManageRepository;
    private final MemoryConfigService memoryConfigService;

    public MemoryManageService(MemoryManageRepository memoryManageRepository,
            MemoryConfigService memoryConfigService) {
        this.memoryManageRepository = memoryManageRepository;
        this.memoryConfigService = memoryConfigService;
    }

    public List<MemoryItem> listItems(MemoryQueryType type, String userId, String agentKey, int page, int pageSize) {
        ensureEnabled();
        return memoryManageRepository.listItems(type, userId, agentKey, page, pageSize);
    }

    public MemoryItem updateContent(MemoryQueryType type, String memoryId, String userId, String agentKey, String content) {
        ensureEnabled();
        memoryManageRepository.updateContent(type, memoryId, userId, agentKey, content);
        return memoryManageRepository.findItem(type, memoryId, userId, agentKey)
                .orElseThrow(() -> new IllegalArgumentException("Memory item not found: " + memoryId));
    }

    public void deleteItem(MemoryQueryType type, String memoryId, String userId, String agentKey) {
        ensureEnabled();
        memoryManageRepository.deleteItem(type, memoryId, userId, agentKey);
    }

    public void clearExecutionHistory(String userId, String agentKey) {
        ensureEnabled();
        memoryManageRepository.clearExecutionHistory(userId, agentKey);
    }

    private void ensureEnabled() {
        if (!memoryConfigService.isManagementEnabled()) {
            throw new IllegalStateException("Memory management is disabled");
        }
    }
}
