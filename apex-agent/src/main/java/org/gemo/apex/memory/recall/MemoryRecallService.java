package org.gemo.apex.memory.recall;

import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.memory.config.MemoryConfigService;
import org.gemo.apex.memory.model.MemoryRecallPackage;
import org.gemo.apex.memory.persistence.repository.MemoryReadRepository;
import org.springframework.stereotype.Service;

/**
 * 记忆召回服务。
 */
@Service
public class MemoryRecallService {

    private static final int DEFAULT_RECALL_LIMIT = 5;

    private final MemoryReadRepository memoryReadRepository;
    private final MemoryConfigService memoryConfigService;

    public MemoryRecallService(MemoryReadRepository memoryReadRepository, MemoryConfigService memoryConfigService) {
        this.memoryReadRepository = memoryReadRepository;
        this.memoryConfigService = memoryConfigService;
    }

    public MemoryRecallPackage recall(SuperAgentContext context) {
        MemoryRecallPackage recallPackage = new MemoryRecallPackage();
        if (!memoryConfigService.isMemoryEnabled()) {
            return recallPackage;
        }
        if (memoryConfigService.isProfileEnabled()) {
            recallPackage.setProfileItems(memoryReadRepository.findProfileItems(context.getUserId(), context.getAgentKey(),
                    DEFAULT_RECALL_LIMIT));
        }
        if (memoryConfigService.isExecutionHistoryEnabled()) {
            recallPackage.setExecutionHistoryItems(memoryReadRepository.findExecutionHistoryItems(context.getUserId(),
                    context.getAgentKey(), context.getSessionId(), DEFAULT_RECALL_LIMIT));
        }
        if (memoryConfigService.isExperienceEnabled()) {
            recallPackage.setExperienceItems(
                    memoryReadRepository.findExperienceItems(context.getAgentKey(), DEFAULT_RECALL_LIMIT));
        }
        return recallPackage;
    }
}
