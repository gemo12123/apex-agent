package org.gemo.apex.memory.persistence.convert;

import org.gemo.apex.constant.ExecutionStatus;
import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.memory.model.SessionRuntimeSnapshot;
import org.gemo.apex.memory.persistence.entity.AgentSessionDialogueMessageEntity;
import org.gemo.apex.memory.persistence.entity.AgentSessionDialogueSummaryEntity;
import org.gemo.apex.memory.persistence.entity.AgentSessionEntity;
import org.gemo.apex.util.JacksonUtils;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话上下文与持久化实体之间的转换器。
 */
public final class SessionContextEntityConverter {

    private SessionContextEntityConverter() {
    }

    public static AgentSessionEntity toSessionEntity(SuperAgentContext context, LocalDateTime createTime) {
        AgentSessionEntity entity = new AgentSessionEntity();
        entity.setSessionId(context.getSessionId());
        entity.setUserId(context.getUserId());
        entity.setAgentKey(context.getAgentKey());
        entity.setExecutionStatus(context.getExecutionStatus() != null ? context.getExecutionStatus().name() : null);
        entity.setCurrentStage(context.getCurrentStage() != null ? context.getCurrentStage().name() : null);
        entity.setExecutionMode(context.getExecutionMode() != null ? context.getExecutionMode().name() : null);
        entity.setLastActiveTime(context.getLastActiveTime());
        entity.setRuntimeSnapshot(JacksonUtils.toJson(toSnapshot(context)));
        entity.setCreateTime(createTime);
        entity.setUpdateTime(LocalDateTime.now());
        return entity;
    }

    public static SessionRuntimeSnapshot toSnapshot(SuperAgentContext context) {
        SessionRuntimeSnapshot snapshot = new SessionRuntimeSnapshot();
        snapshot.setCurrentStageId(context.getCurrentStageId());
        snapshot.setPlan(context.getPlan());
        snapshot.setPendingToolResult(context.getPendingToolResult());
        return snapshot;
    }

    public static List<AgentSessionDialogueMessageEntity> toDialogueEntities(String sessionId, Integer turnNo,
            Long baseSortNo, List<Message> messages) {
        List<AgentSessionDialogueMessageEntity> entities = new ArrayList<>();
        if (messages == null || messages.isEmpty()) {
            return entities;
        }
        long sortNo = baseSortNo != null ? baseSortNo : 0L;
        for (Message message : messages) {
            sortNo++;
            AgentSessionDialogueMessageEntity entity = new AgentSessionDialogueMessageEntity();
            entity.setSessionId(sessionId);
            entity.setTurnNo(turnNo);
            entity.setSortNo(sortNo);
            entity.setRole(MessageEntityConverter.resolveRole(message));
            entity.setMessageType(MessageEntityConverter.resolveMessageType(message));
            entity.setContent(message.getText());
            entity.setToolName(MessageEntityConverter.resolveToolName(message));
            entity.setToolCallId(MessageEntityConverter.resolveToolCallId(message));
            entity.setTokenCount(estimateToken(message));
            entity.setMessagePayload(MessageEntityConverter.toPayload(message));
            entity.setCompacted(Boolean.FALSE);
            entity.setCreateTime(LocalDateTime.now());
            entities.add(entity);
        }
        return entities;
    }

    public static List<AgentSessionDialogueMessageEntity> toDialogueEntities(SuperAgentContext context) {
        return toDialogueEntities(context.getSessionId(), context.getTurnNo(), context.getTurnStartSortNo(),
                context.getDialogueMessages());
    }

    public static AgentSessionDialogueSummaryEntity toSummaryEntity(String sessionId, Message summaryMessage,
            Long compactedToSortNo, Integer turnNo, LocalDateTime createTime) {
        if (summaryMessage == null) {
            return null;
        }
        AgentSessionDialogueSummaryEntity entity = new AgentSessionDialogueSummaryEntity();
        entity.setSessionId(sessionId);
        entity.setRole(MessageEntityConverter.resolveRole(summaryMessage));
        entity.setMessageType("dialogue_summary");
        entity.setContent(summaryMessage.getText());
        entity.setTokenCount(estimateToken(summaryMessage));
        entity.setCompactedToSortNo(compactedToSortNo);
        entity.setSourceTurnNo(turnNo);
        entity.setVersionNo(turnNo != null ? turnNo.longValue() : null);
        entity.setMessagePayload(MessageEntityConverter.toPayload(summaryMessage));
        entity.setCreateTime(createTime);
        entity.setUpdateTime(LocalDateTime.now());
        return entity;
    }

    public static AgentSessionDialogueSummaryEntity toSummaryEntity(SuperAgentContext context, LocalDateTime createTime) {
        return toSummaryEntity(context.getSessionId(), context.getLatestCompressedMessage(),
                context.getLatestCompressedSortNo(), context.getTurnNo(), createTime);
    }

    public static SuperAgentContext fromEntities(AgentSessionEntity sessionEntity,
            AgentSessionDialogueSummaryEntity summaryEntity,
            List<AgentSessionDialogueMessageEntity> dialogueEntities,
            Integer inferredTurnNo) {
        SuperAgentContext context = new SuperAgentContext();
        context.setSessionId(sessionEntity.getSessionId());
        context.setAgentKey(sessionEntity.getAgentKey());
        context.setUserId(sessionEntity.getUserId());
        context.setCurrentStage(parseCurrentStage(sessionEntity.getCurrentStage()));
        if (sessionEntity.getExecutionMode() != null) {
            context.setExecutionMode(ModeEnum.valueOf(sessionEntity.getExecutionMode()));
        }
        if (sessionEntity.getExecutionStatus() != null) {
            context.setExecutionStatus(ExecutionStatus.valueOf(sessionEntity.getExecutionStatus()));
        }
        if (sessionEntity.getLastActiveTime() != null) {
            context.setLastActiveTime(sessionEntity.getLastActiveTime());
        }

        SessionRuntimeSnapshot snapshot = JacksonUtils.fromJson(sessionEntity.getRuntimeSnapshot(),
                SessionRuntimeSnapshot.class);
        if (snapshot != null) {
            context.setCurrentStageId(snapshot.getCurrentStageId());
            context.setPlan(snapshot.getPlan());
            context.setPendingToolResult(snapshot.getPendingToolResult());
        }
        if (summaryEntity != null) {
            context.setLatestCompressedMessage(MessageEntityConverter.fromPayload(summaryEntity.getMessagePayload(),
                    summaryEntity.getRole(), summaryEntity.getContent()));
            context.setLatestCompressedSortNo(summaryEntity.getCompactedToSortNo());
        }
        List<Message> dialogueMessages = new ArrayList<>();
        for (AgentSessionDialogueMessageEntity dialogueEntity : dialogueEntities) {
            dialogueMessages.add(MessageEntityConverter.fromPayload(dialogueEntity.getMessagePayload(),
                    dialogueEntity.getRole(), dialogueEntity.getContent()));
        }
        context.setDialogueMessages(dialogueMessages);
        if (context.getLatestCompressedSortNo() == null) {
            context.setLatestCompressedSortNo(0L);
        }
        context.setTurnStartSortNo(context.getLatestCompressedSortNo());
        int dialogueSize = context.getDialogueMessages().size();
        context.setPersistedDialogueMessageIndex(dialogueSize);
        context.setTurnNo(resolveTurnNo(inferredTurnNo, summaryEntity));
        long nextSortNo = context.getTurnStartSortNo() + dialogueSize + 1L;
        context.setNextMessageSortNo(Math.max(nextSortNo, 1L));
        return context;
    }

    public static Message buildSummaryMessage(String content) {
        return new SystemMessage(content);
    }

    private static int estimateToken(Message message) {
        return message != null && message.getText() != null ? Math.max(1, message.getText().length() / 4) : 0;
    }

    private static int resolveTurnNo(Integer inferredTurnNo, AgentSessionDialogueSummaryEntity summaryEntity) {
        if (inferredTurnNo != null) {
            return inferredTurnNo;
        }
        if (summaryEntity != null && summaryEntity.getSourceTurnNo() != null) {
            return summaryEntity.getSourceTurnNo();
        }
        return 0;
    }

    private static SuperAgentContext.Stage parseCurrentStage(String rawValue) {
        if (rawValue == null) {
            throw new IllegalStateException("Unsupported current stage: null. Only EXECUTION is supported.");
        }
        try {
            SuperAgentContext.Stage stage = SuperAgentContext.Stage.valueOf(rawValue);
            if (stage != SuperAgentContext.Stage.EXECUTION) {
                throw new IllegalStateException(
                        "Unsupported current stage: " + rawValue + ". Only EXECUTION is supported.");
            }
            return stage;
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "Unsupported current stage: " + rawValue + ". Only EXECUTION is supported.", ex);
        }
    }
}
