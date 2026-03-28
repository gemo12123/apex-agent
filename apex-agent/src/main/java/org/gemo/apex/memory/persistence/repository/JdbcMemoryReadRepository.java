package org.gemo.apex.memory.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.gemo.apex.memory.model.MemoryItem;
import org.gemo.apex.memory.persistence.convert.MemoryItemEntityConverter;
import org.gemo.apex.memory.persistence.entity.AgentExperienceMemoryEntity;
import org.gemo.apex.memory.persistence.entity.UserExecutionHistoryMemoryEntity;
import org.gemo.apex.memory.persistence.entity.UserProfileMemoryEntity;
import org.gemo.apex.memory.persistence.mapper.AgentExperienceMemoryMapper;
import org.gemo.apex.memory.persistence.mapper.UserExecutionHistoryMemoryMapper;
import org.gemo.apex.memory.persistence.mapper.UserProfileMemoryMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * JDBC/MyBatis Plus 记忆读仓储。
 */
@Component
@ConditionalOnProperty(prefix = "apex.memory.store", name = "type", havingValue = "jdbc")
public class JdbcMemoryReadRepository implements MemoryReadRepository {

    private final UserProfileMemoryMapper profileMemoryMapper;
    private final UserExecutionHistoryMemoryMapper executionHistoryMemoryMapper;
    private final AgentExperienceMemoryMapper experienceMemoryMapper;

    public JdbcMemoryReadRepository(UserProfileMemoryMapper profileMemoryMapper,
            UserExecutionHistoryMemoryMapper executionHistoryMemoryMapper,
            AgentExperienceMemoryMapper experienceMemoryMapper) {
        this.profileMemoryMapper = profileMemoryMapper;
        this.executionHistoryMemoryMapper = executionHistoryMemoryMapper;
        this.experienceMemoryMapper = experienceMemoryMapper;
    }

    @Override
    public List<MemoryItem> findProfileItems(String userId, String agentKey, int limit) {
        LambdaQueryWrapper<UserProfileMemoryEntity> queryWrapper = new LambdaQueryWrapper<UserProfileMemoryEntity>()
                .eq(UserProfileMemoryEntity::getUserId, userId)
                .eq(UserProfileMemoryEntity::getAgentKey, agentKey)
                .eq(UserProfileMemoryEntity::getStatus, "ACTIVE")
                .orderByDesc(UserProfileMemoryEntity::getObservedTime)
                .last("limit " + limit);
        return profileMemoryMapper.selectList(queryWrapper).stream()
                .map(MemoryItemEntityConverter::fromProfileEntity)
                .toList();
    }

    @Override
    public List<MemoryItem> findExecutionHistoryItems(String userId, String agentKey, String excludeSessionId, int limit) {
        LambdaQueryWrapper<UserExecutionHistoryMemoryEntity> queryWrapper =
                new LambdaQueryWrapper<UserExecutionHistoryMemoryEntity>()
                        .eq(UserExecutionHistoryMemoryEntity::getUserId, userId)
                        .eq(UserExecutionHistoryMemoryEntity::getAgentKey, agentKey)
                        .eq(UserExecutionHistoryMemoryEntity::getStatus, "ACTIVE")
                        .ne(excludeSessionId != null, UserExecutionHistoryMemoryEntity::getSourceSessionId, excludeSessionId)
                        .orderByDesc(UserExecutionHistoryMemoryEntity::getObservedTime)
                        .last("limit " + limit);
        return executionHistoryMemoryMapper.selectList(queryWrapper).stream()
                .map(MemoryItemEntityConverter::fromExecutionEntity)
                .toList();
    }

    @Override
    public List<MemoryItem> findExperienceItems(String agentKey, int limit) {
        LambdaQueryWrapper<AgentExperienceMemoryEntity> queryWrapper =
                new LambdaQueryWrapper<AgentExperienceMemoryEntity>()
                        .eq(AgentExperienceMemoryEntity::getAgentKey, agentKey)
                        .eq(AgentExperienceMemoryEntity::getStatus, "ACTIVE")
                        .orderByDesc(AgentExperienceMemoryEntity::getObservedTime)
                        .last("limit " + limit);
        return experienceMemoryMapper.selectList(queryWrapper).stream()
                .map(MemoryItemEntityConverter::fromExperienceEntity)
                .toList();
    }
}
