package org.gemo.apex.memory.write;

import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.memory.config.MemoryConfigService;
import org.gemo.apex.memory.extract.MemoryExtractionService;
import org.gemo.apex.memory.session.SessionContextStore;
import org.springframework.ai.chat.messages.Message;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 记忆生命周期协调器。
 */
@Slf4j
@Component
public class MemoryLifecycleManager {

    private final MemoryConfigService memoryConfigService;
    private final MemoryExtractionService memoryExtractionService;
    private final MemoryWriteService memoryWriteService;
    private final SessionContextStore sessionContextStore;
    private final ThreadPoolTaskScheduler taskScheduler;
    private final Map<String, ScheduledFuture<?>> longTermTasks = new ConcurrentHashMap<>();

    public MemoryLifecycleManager(MemoryConfigService memoryConfigService,
            MemoryExtractionService memoryExtractionService,
            MemoryWriteService memoryWriteService,
            SessionContextStore sessionContextStore,
            ThreadPoolTaskScheduler taskScheduler) {
        this.memoryConfigService = memoryConfigService;
        this.memoryExtractionService = memoryExtractionService;
        this.memoryWriteService = memoryWriteService;
        this.sessionContextStore = sessionContextStore;
        this.taskScheduler = taskScheduler;
    }

    public void preFlushBeforeCompaction(SuperAgentContext context, List<Message> oldDialogueMessages) {
        memoryWriteService.persistCandidates(memoryExtractionService.extractCompactionCandidates(context, oldDialogueMessages));
    }

    public void onTurnCompleted(SuperAgentContext context) {
        if (!memoryConfigService.isMemoryEnabled()) {
            return;
        }
        if (memoryConfigService.getProperties().getExtraction().isAsyncEnabled()) {
            taskScheduler.execute(() -> executeExecutionHistoryTask(context.getSessionId(), context.getTurnNo()));
        } else {
            executeExecutionHistoryTask(context.getSessionId(), context.getTurnNo());
        }
        scheduleLongTermTask(context);
    }

    private void scheduleLongTermTask(SuperAgentContext context) {
        ScheduledFuture<?> oldTask = longTermTasks.remove(context.getSessionId());
        if (oldTask != null) {
            oldTask.cancel(false);
        }
        Duration delay = Duration.ofMinutes(memoryConfigService.getProperties().getExtraction().getLongTermIdleMinutes());
        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> executeLongTermTask(context.getSessionId(), context.getLastActiveTime()),
                java.util.Date.from(java.time.Instant.now().plus(delay)));
        longTermTasks.put(context.getSessionId(), future);
    }

    private void executeExecutionHistoryTask(String sessionId, Integer turnNo) {
        sessionContextStore.load(sessionId).ifPresent(context -> {
            if (context.getTurnNo() != null && turnNo != null && context.getTurnNo() < turnNo) {
                return;
            }
            List<Message> rawMessages = sessionContextStore.loadAllRawDialogueMessages(sessionId);
            memoryWriteService.persistCandidates(memoryExtractionService.extractExecutionHistoryCandidates(context,
                    rawMessages));
            memoryWriteService.persistCandidates(memoryExtractionService.extractExperienceCandidates(context,
                    rawMessages));
        });
    }

    private void executeLongTermTask(String sessionId, LocalDateTime expectedLastActiveTime) {
        sessionContextStore.load(sessionId).ifPresent(context -> {
            if (context.getLastActiveTime() != null
                    && expectedLastActiveTime != null
                    && context.getLastActiveTime().isAfter(expectedLastActiveTime)) {
                return;
            }
            List<Message> rawMessages = sessionContextStore.loadAllRawDialogueMessages(sessionId);
            memoryWriteService.persistCandidates(memoryExtractionService.extractProfileCandidates(context, rawMessages));
            longTermTasks.remove(sessionId);
            log.info("会话 [{}] 已触发长期记忆抽取", sessionId);
        });
    }
}
