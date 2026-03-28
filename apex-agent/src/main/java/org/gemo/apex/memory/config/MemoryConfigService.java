package org.gemo.apex.memory.config;

import org.springframework.stereotype.Service;

/**
 * 记忆配置读取服务。
 */
@Service
public class MemoryConfigService {

    private final MemoryProperties memoryProperties;

    public MemoryConfigService(MemoryProperties memoryProperties) {
        this.memoryProperties = memoryProperties;
    }

    public MemoryProperties getProperties() {
        return memoryProperties;
    }

    public boolean isMemoryEnabled() {
        return memoryProperties.isEnabled();
    }

    public boolean isProfileEnabled() {
        return memoryProperties.isEnabled() && memoryProperties.isProfileEnabled();
    }

    public boolean isExecutionHistoryEnabled() {
        return memoryProperties.isEnabled() && memoryProperties.isExecutionHistoryEnabled();
    }

    public boolean isExperienceEnabled() {
        return memoryProperties.isEnabled() && memoryProperties.isExperienceEnabled();
    }

    public boolean isCompactionEnabled() {
        return memoryProperties.isEnabled() && memoryProperties.getCompaction().isEnabled();
    }

    public boolean isManagementEnabled() {
        return memoryProperties.isEnabled() && memoryProperties.isManagementEnabled();
    }
}
