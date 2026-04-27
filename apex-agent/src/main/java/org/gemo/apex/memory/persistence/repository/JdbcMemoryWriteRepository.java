package org.gemo.apex.memory.persistence.repository;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.memory.model.MemoryItem;
import org.gemo.apex.memory.persistence.convert.MemoryItemEntityConverter;
import org.gemo.apex.memory.persistence.entity.AgentExperienceMemoryEntity;
import org.gemo.apex.memory.persistence.entity.UserExecutionHistoryMemoryEntity;
import org.gemo.apex.memory.persistence.entity.UserProfileMemoryEntity;
import org.gemo.apex.memory.persistence.mapper.AgentExperienceMemoryMapper;
import org.gemo.apex.memory.persistence.mapper.UserExecutionHistoryMemoryMapper;
import org.gemo.apex.memory.persistence.mapper.UserProfileMemoryMapper;
import org.gemo.apex.memory.search.MemoryEmbeddingService;
import org.gemo.apex.memory.search.PostgresSearchIndexUpdater;
import org.gemo.apex.memory.search.SearchIndexTextBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * JDBC/MyBatis Plus 记忆写仓储。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "apex.memory.store", name = "type", havingValue = "jdbc")
public class JdbcMemoryWriteRepository implements MemoryWriteRepository {

    private final UserProfileMemoryMapper profileMemoryMapper;
    private final UserExecutionHistoryMemoryMapper executionHistoryMemoryMapper;
    private final AgentExperienceMemoryMapper experienceMemoryMapper;
    private final SearchIndexTextBuilder searchIndexTextBuilder;
    private final MemoryEmbeddingService memoryEmbeddingService;
    private final PostgresSearchIndexUpdater searchIndexUpdater;

    public JdbcMemoryWriteRepository(UserProfileMemoryMapper profileMemoryMapper,
            UserExecutionHistoryMemoryMapper executionHistoryMemoryMapper,
            AgentExperienceMemoryMapper experienceMemoryMapper,
            SearchIndexTextBuilder searchIndexTextBuilder,
            MemoryEmbeddingService memoryEmbeddingService,
            PostgresSearchIndexUpdater searchIndexUpdater) {
        this.profileMemoryMapper = profileMemoryMapper;
        this.executionHistoryMemoryMapper = executionHistoryMemoryMapper;
        this.experienceMemoryMapper = experienceMemoryMapper;
        this.searchIndexTextBuilder = searchIndexTextBuilder;
        this.memoryEmbeddingService = memoryEmbeddingService;
        this.searchIndexUpdater = searchIndexUpdater;
    }

    @Override
    public void upsertProfileItem(MemoryItem item) {
        UserProfileMemoryEntity existing = profileMemoryMapper.selectOne(
                new LambdaQueryWrapper<UserProfileMemoryEntity>()
                        .eq(UserProfileMemoryEntity::getUserId, item.getUserId())
                        .eq(UserProfileMemoryEntity::getAgentKey, item.getAgentKey())
                        .eq(UserProfileMemoryEntity::getMemoryKey, item.getMemoryKey())
                        .last("limit 1"));
        UserProfileMemoryEntity entity = MemoryItemEntityConverter.toProfileEntity(item);
        if (existing == null) {
            entity.setId(item.getId() != null ? item.getId() : IdUtil.simpleUUID());
            entity.setCreateTime(LocalDateTime.now());
            profileMemoryMapper.insert(entity);
        } else {
            entity.setId(existing.getId());
            entity.setCreateTime(existing.getCreateTime());
            profileMemoryMapper.updateById(entity);
        }
    }

    @Override
    public void upsertExecutionHistoryItem(MemoryItem item) {
        UserExecutionHistoryMemoryEntity existing = executionHistoryMemoryMapper.selectOne(
                new LambdaQueryWrapper<UserExecutionHistoryMemoryEntity>()
                        .eq(UserExecutionHistoryMemoryEntity::getUserId, item.getUserId())
                        .eq(UserExecutionHistoryMemoryEntity::getAgentKey, item.getAgentKey())
                        .eq(UserExecutionHistoryMemoryEntity::getTopicKey, item.getTopicKey())
                        .eq(UserExecutionHistoryMemoryEntity::getSourceSessionId, item.getSourceSessionId())
                        .eq(UserExecutionHistoryMemoryEntity::getTimeScope, item.getTimeScope() != null ? item.getTimeScope().name() : null)
                        .last("limit 1"));
        if (existing != null && existing.getVersionNo() != null && item.getVersionNo() != null
                && existing.getVersionNo() > item.getVersionNo()) {
            return;
        }
        UserExecutionHistoryMemoryEntity entity = MemoryItemEntityConverter.toExecutionEntity(item);
        entity.setSearchText(searchIndexTextBuilder.buildExecutionHistoryText(item));
        if (existing == null) {
            entity.setId(item.getId() != null ? item.getId() : IdUtil.simpleUUID());
            entity.setCreateTime(LocalDateTime.now());
            executionHistoryMemoryMapper.insert(entity);
        } else {
            entity.setId(existing.getId());
            entity.setCreateTime(existing.getCreateTime());
            executionHistoryMemoryMapper.updateById(entity);
        }
        try {
            searchIndexUpdater.refreshExecutionHistory(entity.getId(), entity.getSearchText(),
                    memoryEmbeddingService.embed(entity.getSearchText()));
        } catch (RuntimeException ex) {
            log.warn("Failed to refresh execution history search index, id={}", entity.getId(), ex);
        }
    }

    @Override
    public void upsertExperienceItem(MemoryItem item) {
        AgentExperienceMemoryEntity existing = experienceMemoryMapper.selectOne(
                new LambdaQueryWrapper<AgentExperienceMemoryEntity>()
                        .eq(AgentExperienceMemoryEntity::getAgentKey, item.getAgentKey())
                        .eq(AgentExperienceMemoryEntity::getTopicKey, item.getTopicKey())
                        .eq(AgentExperienceMemoryEntity::getMemoryKey, item.getMemoryKey())
                        .last("limit 1"));
        AgentExperienceMemoryEntity entity = MemoryItemEntityConverter.toExperienceEntity(item);
        if (existing == null) {
            entity.setId(item.getId() != null ? item.getId() : IdUtil.simpleUUID());
            entity.setCreateTime(LocalDateTime.now());
            experienceMemoryMapper.insert(entity);
        } else {
            entity.setId(existing.getId());
            entity.setCreateTime(existing.getCreateTime());
            experienceMemoryMapper.updateById(entity);
        }
    }
}
