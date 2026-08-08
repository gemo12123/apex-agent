package org.gemo.apex.protocol.event.detail;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public final class AskHumanInterventionDetail implements HumanInterventionDetail {
    @JsonProperty("tool_call_id")
    private String toolCallId;

    @JsonProperty("invocation_id")
    private String invocationId;

    @JsonProperty("tool_name")
    private String toolName;

    @JsonProperty("questions")
    private List<AskHumanQuestionDetail> questions;
}
