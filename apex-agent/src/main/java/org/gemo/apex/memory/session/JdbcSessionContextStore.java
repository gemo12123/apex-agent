package org.gemo.apex.memory.session;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.memory.persistence.convert.MessageEntityConverter;
import org.gemo.apex.memory.persistence.convert.SessionContextEntityConverter;
import org.gemo.apex.memory.persistence.entity.AgentSessionDialogueMessageEntity;
import org.gemo.apex.memory.persistence.entity.AgentSessionDialogueSummaryEntity;
import org.gemo.apex.memory.persistence.entity.AgentSessionEntity;
import org.gemo.apex.memory.persistence.mapper.AgentSessionDialogueMessageMapper;
import org.gemo.apex.memory.persistence.mapper.AgentSessionDialogueSummaryMapper;
import org.gemo.apex.memory.persistence.mapper.AgentSessionMapper;
import org.springframework.ai.chat.messages.Message;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 基于数据库的会话上下文存储。
 * 类名使用 Jdbc 前缀，但内部实际通过 MyBatis Plus 完成持久化。
 */
@Component
@ConditionalOnProperty(prefix = "apex.memory.store", name = "type", havingValue = "jdbc")
public class JdbcSessionContextStore implements SessionContextStore {

    private final AgentSessionMapper sessionMapper;
    private final AgentSessionDialogueMessageMapper dialogueMessageMapper;
    private final AgentSessionDialogueSummaryMapper dialogueSummaryMapper;

    public JdbcSessionContextStore(AgentSessionMapper sessionMapper,
            AgentSessionDialogueMessageMapper dialogueMessageMapper,
            AgentSessionDialogueSummaryMapper dialogueSummaryMapper) {
        this.sessionMapper = sessionMapper;
        this.dialogueMessageMapper = dialogueMessageMapper;
        this.dialogueSummaryMapper = dialogueSummaryMapper;
    }

    @Override
    public Optional<SuperAgentContext> load(String sessionId) {
        AgentSessionEntity sessionEntity = sessionMapper.selectById(sessionId);
        if (sessionEntity == null) {
            return Optional.empty();
        }
        AgentSessionDialogueSummaryEntity summaryEntity = dialogueSummaryMapper.selectById(sessionId);
        List<AgentSessionDialogueMessageEntity> dialogueEntities = dialogueMessageMapper.selectList(
                new LambdaQueryWrapper<AgentSessionDialogueMessageEntity>()
                        .eq(AgentSessionDialogueMessageEntity::getSessionId, sessionId)
                        .eq(AgentSessionDialogueMessageEntity::getCompacted, false)
                        .orderByAsc(AgentSessionDialogueMessageEntity::getSortNo));
        AgentSessionDialogueMessageEntity latestTurnEntity = dialogueMessageMapper.selectOne(
                new LambdaQueryWrapper<AgentSessionDialogueMessageEntity>()
                        .select(AgentSessionDialogueMessageEntity::getTurnNo)
                        .eq(AgentSessionDialogueMessageEntity::getSessionId, sessionId)
                        .isNotNull(AgentSessionDialogueMessageEntity::getTurnNo)
                        .orderByDesc(AgentSessionDialogueMessageEntity::getTurnNo)
                        .last("LIMIT 1"));
        Integer inferredTurnNo = latestTurnEntity != null ? latestTurnEntity.getTurnNo() : null;
        return Optional.of(SessionContextEntityConverter.fromEntities(sessionEntity, summaryEntity, dialogueEntities,
                inferredTurnNo));
    }

    @Override
    public void save(SuperAgentContext context) {
        AgentSessionEntity existing = sessionMapper.selectById(context.getSessionId());
        LocalDateTime createTime = existing != null ? existing.getCreateTime() : LocalDateTime.now();
        AgentSessionEntity sessionEntity = SessionContextEntityConverter.toSessionEntity(context, createTime);
        if (existing == null) {
            sessionMapper.insert(sessionEntity);
        } else {
            sessionMapper.updateById(sessionEntity);
        }
    }

    @Override
    @Transactional
    public void appendDialogueMessages(String sessionId, Integer turnNo, Long baseSortNo, List<Message> messages) {
        for (AgentSessionDialogueMessageEntity entity : SessionContextEntityConverter.toDialogueEntities(sessionId,
                turnNo, baseSortNo, messages)) {
            dialogueMessageMapper.insert(entity);
        }
    }

    @Override
    public List<Message> loadAllRawDialogueMessages(String sessionId) {
        return dialogueMessageMapper.selectList(new LambdaQueryWrapper<AgentSessionDialogueMessageEntity>()
                .eq(AgentSessionDialogueMessageEntity::getSessionId, sessionId)
                .orderByAsc(AgentSessionDialogueMessageEntity::getSortNo)).stream()
                .map(entity -> MessageEntityConverter.fromPayload(entity.getMessagePayload(), entity.getRole(),
                        entity.getContent()))
                .toList();
    }

    @Override
    public int countUncompactedMessagesBeforeTurn(String sessionId, Integer turnNo) {
        LambdaQueryWrapper<AgentSessionDialogueMessageEntity> queryWrapper =
                new LambdaQueryWrapper<AgentSessionDialogueMessageEntity>()
                        .eq(AgentSessionDialogueMessageEntity::getSessionId, sessionId)
                        .eq(AgentSessionDialogueMessageEntity::getCompacted, false);
        if (turnNo != null) {
            queryWrapper.lt(AgentSessionDialogueMessageEntity::getTurnNo, turnNo);
        }
        Long count = dialogueMessageMapper.selectCount(queryWrapper);
        return count != null ? count.intValue() : 0;
    }

    @Override
    @Transactional
    public void compactDialogue(String sessionId, Message summaryMessage, Long compactedToSortNo, Integer turnNo) {
        if (summaryMessage == null || compactedToSortNo == null) {
            return;
        }

        AgentSessionDialogueSummaryEntity existingSummary = dialogueSummaryMapper.selectById(sessionId);
        LocalDateTime createTime = existingSummary != null ? existingSummary.getCreateTime() : LocalDateTime.now();
        AgentSessionDialogueSummaryEntity summaryEntity = SessionContextEntityConverter.toSummaryEntity(sessionId,
                summaryMessage, compactedToSortNo, turnNo, createTime);
        if (existingSummary == null) {
            dialogueSummaryMapper.insert(summaryEntity);
        } else {
            dialogueSummaryMapper.updateById(summaryEntity);
        }

        LambdaUpdateWrapper<AgentSessionDialogueMessageEntity> updateWrapper =
                new LambdaUpdateWrapper<AgentSessionDialogueMessageEntity>()
                        .eq(AgentSessionDialogueMessageEntity::getSessionId, sessionId)
                        .eq(AgentSessionDialogueMessageEntity::getCompacted, false)
                        .le(AgentSessionDialogueMessageEntity::getSortNo, compactedToSortNo);
        if (turnNo != null) {
            updateWrapper.lt(AgentSessionDialogueMessageEntity::getTurnNo, turnNo);
        }
        updateWrapper.set(AgentSessionDialogueMessageEntity::getCompacted, true);
        dialogueMessageMapper.update(null, updateWrapper);
    }

    @Override
    @Transactional
    public void delete(String sessionId) {
        dialogueMessageMapper.delete(new LambdaQueryWrapper<AgentSessionDialogueMessageEntity>()
                .eq(AgentSessionDialogueMessageEntity::getSessionId, sessionId));
        dialogueSummaryMapper.deleteById(sessionId);
        sessionMapper.deleteById(sessionId);
    }
}
