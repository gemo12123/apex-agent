package org.gemo.apex.memory.persistence.repository;

import org.gemo.apex.memory.model.MemoryItem;

import java.util.List;

/**
 * 记忆读仓储接口。
 */
public interface MemoryReadRepository {

    /**
     * 读取用户画像记忆。
     */
    List<MemoryItem> findProfileItems(String userId, String agentKey, int limit);

    /**
     * 读取用户执行历史记忆，并排除当前会话。
     */
    List<MemoryItem> findExecutionHistoryItems(String userId, String agentKey, String excludeSessionId, int limit);

    /**
     * 读取智能体经验记忆。
     */
    List<MemoryItem> findExperienceItems(String agentKey, int limit);
}
