package org.gemo.apex.memory.persistence.repository;

import org.gemo.apex.memory.model.MemoryItem;
import org.gemo.apex.memory.model.MemoryQueryType;

import java.util.List;
import java.util.Optional;

/**
 * 记忆管理仓储接口。
 */
public interface MemoryManageRepository {

    /**
     * 分页查询指定类型记忆。
     */
    List<MemoryItem> listItems(MemoryQueryType type, String userId, String agentKey, int page, int pageSize);

    /**
     * 获取单条记忆。
     */
    Optional<MemoryItem> findItem(MemoryQueryType type, String memoryId, String userId, String agentKey);

    /**
     * 更新记忆内容。
     */
    void updateContent(MemoryQueryType type, String memoryId, String userId, String agentKey, String content);

    /**
     * 删除单条记忆。
     */
    void deleteItem(MemoryQueryType type, String memoryId, String userId, String agentKey);

    /**
     * 清空指定用户在某个 Agent 下的执行历史。
     */
    void clearExecutionHistory(String userId, String agentKey);
}
