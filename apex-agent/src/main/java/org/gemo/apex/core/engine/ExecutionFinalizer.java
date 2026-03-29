package org.gemo.apex.core.engine;

import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.memory.session.SessionContextStore;
import org.gemo.apex.memory.write.MemoryLifecycleManager;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class ExecutionFinalizer {

    private final SessionContextStore sessionContextStore;
    private final MemoryLifecycleManager memoryLifecycleManager;

    public ExecutionFinalizer(SessionContextStore sessionContextStore,
            MemoryLifecycleManager memoryLifecycleManager) {
        this.sessionContextStore = sessionContextStore;
        this.memoryLifecycleManager = memoryLifecycleManager;
    }

    public void finalizeTurn(SuperAgentContext context) {
        persistDialogueMessages(context);
        context.setNextMessageSortNo(context.getTurnStartSortNo() + context.getPersistedDialogueMessageIndex() + 1L);
        context.setLastActiveTime(LocalDateTime.now());
        sessionContextStore.save(context);
        memoryLifecycleManager.onTurnCompleted(context);
    }

    private void persistDialogueMessages(SuperAgentContext context) {
        int persistedIndex = context.getPersistedDialogueMessageIndex() != null
                ? context.getPersistedDialogueMessageIndex()
                : 0;
        if (persistedIndex >= context.getDialogueMessages().size()) {
            return;
        }
        List<Message> messagesToPersist = new ArrayList<>(
                context.getDialogueMessages().subList(persistedIndex, context.getDialogueMessages().size()));
        long baseSortNo = (context.getTurnStartSortNo() != null ? context.getTurnStartSortNo() : 0L) + persistedIndex;
        sessionContextStore.appendDialogueMessages(context.getSessionId(), context.getTurnNo(), baseSortNo,
                messagesToPersist);
        context.setPersistedDialogueMessageIndex(context.getDialogueMessages().size());
    }
}
