package org.gemo.apex.hook.lifecycle;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;

@Data
@Builder
@Jacksonized
public class HookExecutionRecord {
    private HookPoint hookPoint;
    private String hookBean;
    private int order;
    private HookFlowAction action;
    private boolean succeeded;
    private String error;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}
