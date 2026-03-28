package org.gemo.apex.memory.persistence.repository;

import org.gemo.apex.memory.model.MemoryItem;

/**
 * 记忆写仓储接口。
 */
public interface MemoryWriteRepository {

    /**
     * 写入或更新用户画像记忆。
     */
    void upsertProfileItem(MemoryItem item);

    /**
     * 写入或更新执行历史记忆。
     */
    void upsertExecutionHistoryItem(MemoryItem item);

    /**
     * 写入或更新智能体经验记忆。
     */
    void upsertExperienceItem(MemoryItem item);
}
