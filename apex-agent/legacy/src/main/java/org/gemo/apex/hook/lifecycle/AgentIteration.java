package org.gemo.apex.hook.lifecycle;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@Jacksonized
public class AgentIteration {

    public enum Status {
        IN_PROGRESS,
        COMPLETED,
        SKIPPED,
        SUSPENDED,
        FAILED
    }

    private long turnNo;
    private int iterationNo;
    @Builder.Default
    private Status status = Status.IN_PROGRESS;
    @Builder.Default
    private List<Message> modelInput = new ArrayList<>();
    private ChatResponse originalModelOutput;
    private Message finalModelOutput;
    @Builder.Default
    private List<ToolCallRecord> toolCalls = new ArrayList<>();
    @Builder.Default
    private List<HookExecutionRecord> hookExecutions = new ArrayList<>();
    @Builder.Default
    private List<MessageMutationRecord> messageMutations = new ArrayList<>();
    private HookFlowAction flowAction;
    private String error;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}
