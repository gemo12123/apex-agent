package org.gemo.apex.memory.write;

import org.gemo.apex.memory.config.MemoryConfigService;
import org.gemo.apex.memory.model.MemoryItem;
import org.gemo.apex.memory.persistence.repository.MemoryWriteRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 记忆写入服务。
 */
@Service
public class MemoryWriteService {

    private final MemoryWriteRepository memoryWriteRepository;
    private final MemoryConfigService memoryConfigService;

    public MemoryWriteService(MemoryWriteRepository memoryWriteRepository,
            MemoryConfigService memoryConfigService) {
        this.memoryWriteRepository = memoryWriteRepository;
        this.memoryConfigService = memoryConfigService;
    }

    public void persistCandidates(List<MemoryItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (MemoryItem item : items) {
            if (item == null || item.getType() == null) {
                continue;
            }
            switch (item.getType()) {
                case LONG_TERM, FACT -> {
                    if (memoryConfigService.isProfileEnabled()) {
                        memoryWriteRepository.upsertProfileItem(item);
                    }
                }
                case EXECUTION_HISTORY -> {
                    if (memoryConfigService.isExecutionHistoryEnabled()) {
                        memoryWriteRepository.upsertExecutionHistoryItem(item);
                    }
                }
                case AGENT_EXPERIENCE -> {
                    if (memoryConfigService.isExperienceEnabled()) {
                        memoryWriteRepository.upsertExperienceItem(item);
                    }
                }
            }
        }
    }
}
