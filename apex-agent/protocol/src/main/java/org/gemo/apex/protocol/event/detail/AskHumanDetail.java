package org.gemo.apex.protocol.event.detail;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
public class AskHumanDetail {
    @JsonProperty("input_type") private String inputType;
    @JsonProperty("question") private String question;
    @JsonProperty("description") private String description;
    @JsonProperty("options") private List<AskHumanOption> options;
    @JsonProperty("tool_call_id") private String toolCallId;
}
