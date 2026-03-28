package org.gemo.apex.memory.session;

import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.memory.persistence.convert.MessageEntityConverter;
import org.gemo.apex.memory.persistence.convert.SessionContextEntityConverter;
import org.gemo.apex.memory.persistence.entity.AgentSessionDialogueMessageEntity;
import org.gemo.apex.memory.persistence.entity.AgentSessionDialogueSummaryEntity;
import org.gemo.apex.memory.persistence.entity.AgentSessionEntity;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存的会话上下文存储。
 */
@Component
public class InMemorySessionContextStore implements SessionContextStore {

    private final Map<String, AgentSessionEntity> sessionEntityMap = new ConcurrentHashMap<>();
    private final Map<String, List<AgentSessionDialogueMessageEntity>> dialogueMessageMap = new ConcurrentHashMap<>();
    private final Map<String, AgentSessionDialogueSummaryEntity> summaryEntityMap = new ConcurrentHashMap<>();

    @Override
    public Optional<SuperAgentContext> load(String sessionId) {
        AgentSessionEntity sessionEntity = sessionEntityMap.get(sessionId);
        if (sessionEntity == null) {
            return Optional.empty();
        }

        AgentSessionDialogueSummaryEntity summaryEntity = summaryEntityMap.get(sessionId);
        List<AgentSessionDialogueMessageEntity> dialogueEntities = dialogueMessageMap
                .getOrDefault(sessionId, List.of())
                .stream()
                .filter(entity -> !Boolean.TRUE.equals(entity.getCompacted()))
                .sorted(Comparator.comparing(AgentSessionDialogueMessageEntity::getSortNo))
                .toList();
        Integer inferredTurnNo = dialogueMessageMap.getOrDefault(sessionId, List.of()).stream()
                .map(AgentSessionDialogueMessageEntity::getTurnNo)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(null);
        return Optional.of(SessionContextEntityConverter.fromEntities(sessionEntity, summaryEntity, dialogueEntities,
                inferredTurnNo));
    }

    @Override
    public void save(SuperAgentContext context) {
        if (context == null || context.getSessionId() == null) {
            return;
        }
        AgentSessionEntity existing = sessionEntityMap.get(context.getSessionId());
        LocalDateTime createTime = existing != null ? existing.getCreateTime() : LocalDateTime.now();
        sessionEntityMap.put(context.getSessionId(), SessionContextEntityConverter.toSessionEntity(context, createTime));
    }

    @Override
    public void appendDialogueMessages(String sessionId, Integer turnNo, Long baseSortNo, List<Message> messages) {
        if (sessionId == null || messages == null || messages.isEmpty()) {
            return;
        }
        List<AgentSessionDialogueMessageEntity> existing = new ArrayList<>(
                dialogueMessageMap.getOrDefault(sessionId, List.of()));
        existing.addAll(SessionContextEntityConverter.toDialogueEntities(sessionId, turnNo, baseSortNo, messages));
        existing.sort(Comparator.comparing(AgentSessionDialogueMessageEntity::getSortNo));
        dialogueMessageMap.put(sessionId, existing);
    }

    @Override
    public List<Message> loadAllRawDialogueMessages(String sessionId) {
        return dialogueMessageMap.getOrDefault(sessionId, List.of()).stream()
                .sorted(Comparator.comparing(AgentSessionDialogueMessageEntity::getSortNo))
                .map(entity -> MessageEntityConverter.fromPayload(entity.getMessagePayload(), entity.getRole(),
                        entity.getContent()))
                .toList();
    }

    @Override
    public int countUncompactedMessagesBeforeTurn(String sessionId, Integer turnNo) {
        return (int) dialogueMessageMap.getOrDefault(sessionId, List.of()).stream()
                .filter(entity -> !Boolean.TRUE.equals(entity.getCompacted()))
                .filter(entity -> turnNo == null || entity.getTurnNo() == null || entity.getTurnNo() < turnNo)
                .count();
    }

    @Override
    public void compactDialogue(String sessionId, Message summaryMessage, Long compactedToSortNo, Integer turnNo) {
        if (sessionId == null || summaryMessage == null || compactedToSortNo == null) {
            return;
        }
        List<AgentSessionDialogueMessageEntity> existing = new ArrayList<>(
                dialogueMessageMap.getOrDefault(sessionId, List.of()));
        for (AgentSessionDialogueMessageEntity entity : existing) {
            if (Boolean.TRUE.equals(entity.getCompacted())) {
                continue;
            }
            if (entity.getSortNo() == null || entity.getSortNo() > compactedToSortNo) {
                continue;
            }
            if (turnNo != null && entity.getTurnNo() != null && entity.getTurnNo() >= turnNo) {
                continue;
            }
            entity.setCompacted(Boolean.TRUE);
        }
        dialogueMessageMap.put(sessionId, existing);

        AgentSessionDialogueSummaryEntity existingSummary = summaryEntityMap.get(sessionId);
        LocalDateTime createTime = existingSummary != null ? existingSummary.getCreateTime() : LocalDateTime.now();
        summaryEntityMap.put(sessionId, SessionContextEntityConverter.toSummaryEntity(sessionId, summaryMessage,
                compactedToSortNo, turnNo, createTime));
    }

    @Override
    public void delete(String sessionId) {
        sessionEntityMap.remove(sessionId);
        dialogueMessageMap.remove(sessionId);
        summaryEntityMap.remove(sessionId);
    }
}
