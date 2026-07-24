package org.gemo.apex.domain.interaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingToolExecution {
    private String toolCallId;
    private String toolName;
    private String invocationId;
    private Map<String, Object> resolvedArguments;
    private List<String> editableFieldKeys;
    private String confirmationId;
    private List<String> executedPreHookBeans;
    private Long turnNo;
    private Integer traceNo;
    private Integer toolIndex;
}
