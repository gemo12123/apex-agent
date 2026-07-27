package org.gemo.apex.hook.lifecycle;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@Jacksonized
public class AgentTurn {

    public enum Status {
        IN_PROGRESS,
        SUSPENDED,
        COMPLETED,
        ENDED_BY_HOOK,
        FAILED
    }

    private long turnNo;
    private String sessionId;
    private String agentKey;
    private String userId;
    @Builder.Default
    private Status status = Status.IN_PROGRESS;
    private int lastIterationNo;
    @Builder.Default
    private List<HookExecutionRecord> hookExecutions = new ArrayList<>();
    @Builder.Default
    private List<MessageMutationRecord> messageMutations = new ArrayList<>();
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}
