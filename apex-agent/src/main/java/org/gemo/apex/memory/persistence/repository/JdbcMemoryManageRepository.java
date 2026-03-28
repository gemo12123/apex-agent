package org.gemo.apex.memory.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.gemo.apex.memory.model.MemoryItem;
import org.gemo.apex.memory.model.MemoryQueryType;
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
import java.util.Optional;

/**
 * JDBC/MyBatis Plus 记忆管理仓储。
 */
@Component
@ConditionalOnProperty(prefix = "apex.memory.store", name = "type", havingValue = "jdbc")
public class JdbcMemoryManageRepository implements MemoryManageRepository {

    private final UserProfileMemoryMapper profileMemoryMapper;
    private final UserExecutionHistoryMemoryMapper executionHistoryMemoryMapper;
    private final AgentExperienceMemoryMapper experienceMemoryMapper;

    public JdbcMemoryManageRepository(UserProfileMemoryMapper profileMemoryMapper,
            UserExecutionHistoryMemoryMapper executionHistoryMemoryMapper,
            AgentExperienceMemoryMapper experienceMemoryMapper) {
        this.profileMemoryMapper = profileMemoryMapper;
        this.executionHistoryMemoryMapper = executionHistoryMemoryMapper;
        this.experienceMemoryMapper = experienceMemoryMapper;
    }

    @Override
    public List<MemoryItem> listItems(MemoryQueryType type, String userId, String agentKey, int page, int pageSize) {
        int offset = Math.max(0, page - 1) * pageSize;
        String pageClause = "limit " + pageSize + " offset " + offset;
        return switch (type) {
            case PROFILE -> profileMemoryMapper.selectList(new LambdaQueryWrapper<UserProfileMemoryEntity>()
                    .eq(UserProfileMemoryEntity::getUserId, userId)
                    .eq(UserProfileMemoryEntity::getAgentKey, agentKey)
                    .ne(UserProfileMemoryEntity::getStatus, "DELETED")
                    .orderByDesc(UserProfileMemoryEntity::getObservedTime)
                    .last(pageClause)).stream().map(MemoryItemEntityConverter::fromProfileEntity).toList();
            case EXECUTION_HISTORY -> executionHistoryMemoryMapper
                    .selectList(new LambdaQueryWrapper<UserExecutionHistoryMemoryEntity>()
                            .eq(UserExecutionHistoryMemoryEntity::getUserId, userId)
                            .eq(UserExecutionHistoryMemoryEntity::getAgentKey, agentKey)
                            .ne(UserExecutionHistoryMemoryEntity::getStatus, "DELETED")
                            .orderByDesc(UserExecutionHistoryMemoryEntity::getObservedTime)
                            .last(pageClause))
                    .stream()
                    .map(MemoryItemEntityConverter::fromExecutionEntity)
                    .toList();
            case EXPERIENCE -> experienceMemoryMapper.selectList(new LambdaQueryWrapper<AgentExperienceMemoryEntity>()
                    .eq(AgentExperienceMemoryEntity::getAgentKey, agentKey)
                    .ne(AgentExperienceMemoryEntity::getStatus, "DELETED")
                    .orderByDesc(AgentExperienceMemoryEntity::getObservedTime)
                    .last(pageClause)).stream().map(MemoryItemEntityConverter::fromExperienceEntity).toList();
        };
    }

    @Override
    public Optional<MemoryItem> findItem(MemoryQueryType type, String memoryId, String userId, String agentKey) {
        return switch (type) {
            case PROFILE -> Optional.ofNullable(profileMemoryMapper.selectOne(new LambdaQueryWrapper<UserProfileMemoryEntity>()
                            .eq(UserProfileMemoryEntity::getId, memoryId)
                            .eq(UserProfileMemoryEntity::getUserId, userId)
                            .eq(UserProfileMemoryEntity::getAgentKey, agentKey)
                            .last("limit 1")))
                    .map(MemoryItemEntityConverter::fromProfileEntity);
            case EXECUTION_HISTORY -> Optional.ofNullable(executionHistoryMemoryMapper.selectOne(
                            new LambdaQueryWrapper<UserExecutionHistoryMemoryEntity>()
                                    .eq(UserExecutionHistoryMemoryEntity::getId, memoryId)
                                    .eq(UserExecutionHistoryMemoryEntity::getUserId, userId)
                                    .eq(UserExecutionHistoryMemoryEntity::getAgentKey, agentKey)
                                    .last("limit 1")))
                    .map(MemoryItemEntityConverter::fromExecutionEntity);
            case EXPERIENCE -> Optional.ofNullable(experienceMemoryMapper.selectOne(new LambdaQueryWrapper<AgentExperienceMemoryEntity>()
                            .eq(AgentExperienceMemoryEntity::getId, memoryId)
                            .eq(AgentExperienceMemoryEntity::getAgentKey, agentKey)
                            .last("limit 1")))
                    .map(MemoryItemEntityConverter::fromExperienceEntity);
        };
    }

    @Override
    public void updateContent(MemoryQueryType type, String memoryId, String userId, String agentKey, String content) {
        switch (type) {
            case PROFILE -> findProfile(memoryId, userId, agentKey).ifPresent(entity -> {
                entity.setContent(content);
                profileMemoryMapper.updateById(entity);
            });
            case EXECUTION_HISTORY -> findExecution(memoryId, userId, agentKey).ifPresent(entity -> {
                entity.setContent(content);
                executionHistoryMemoryMapper.updateById(entity);
            });
            case EXPERIENCE -> findExperience(memoryId, agentKey).ifPresent(entity -> {
                entity.setContent(content);
                experienceMemoryMapper.updateById(entity);
            });
        }
    }

    @Override
    public void deleteItem(MemoryQueryType type, String memoryId, String userId, String agentKey) {
        switch (type) {
            case PROFILE -> findProfile(memoryId, userId, agentKey).ifPresent(entity -> {
                entity.setStatus("DELETED");
                profileMemoryMapper.updateById(entity);
            });
            case EXECUTION_HISTORY -> findExecution(memoryId, userId, agentKey).ifPresent(entity -> {
                entity.setStatus("DELETED");
                executionHistoryMemoryMapper.updateById(entity);
            });
            case EXPERIENCE -> findExperience(memoryId, agentKey).ifPresent(entity -> {
                entity.setStatus("DELETED");
                experienceMemoryMapper.updateById(entity);
            });
        }
    }

    @Override
    public void clearExecutionHistory(String userId, String agentKey) {
        List<UserExecutionHistoryMemoryEntity> entities = executionHistoryMemoryMapper.selectList(
                new LambdaQueryWrapper<UserExecutionHistoryMemoryEntity>()
                        .eq(UserExecutionHistoryMemoryEntity::getUserId, userId)
                        .eq(UserExecutionHistoryMemoryEntity::getAgentKey, agentKey));
        for (UserExecutionHistoryMemoryEntity entity : entities) {
            entity.setStatus("DELETED");
            executionHistoryMemoryMapper.updateById(entity);
        }
    }

    private Optional<UserProfileMemoryEntity> findProfile(String memoryId, String userId, String agentKey) {
        return Optional.ofNullable(profileMemoryMapper.selectOne(new LambdaQueryWrapper<UserProfileMemoryEntity>()
                .eq(UserProfileMemoryEntity::getId, memoryId)
                .eq(UserProfileMemoryEntity::getUserId, userId)
                .eq(UserProfileMemoryEntity::getAgentKey, agentKey)
                .last("limit 1")));
    }

    private Optional<UserExecutionHistoryMemoryEntity> findExecution(String memoryId, String userId, String agentKey) {
        return Optional.ofNullable(executionHistoryMemoryMapper.selectOne(new LambdaQueryWrapper<UserExecutionHistoryMemoryEntity>()
                .eq(UserExecutionHistoryMemoryEntity::getId, memoryId)
                .eq(UserExecutionHistoryMemoryEntity::getUserId, userId)
                .eq(UserExecutionHistoryMemoryEntity::getAgentKey, agentKey)
                .last("limit 1")));
    }

    private Optional<AgentExperienceMemoryEntity> findExperience(String memoryId, String agentKey) {
        return Optional.ofNullable(experienceMemoryMapper.selectOne(new LambdaQueryWrapper<AgentExperienceMemoryEntity>()
                .eq(AgentExperienceMemoryEntity::getId, memoryId)
                .eq(AgentExperienceMemoryEntity::getAgentKey, agentKey)
                .last("limit 1")));
    }
}
