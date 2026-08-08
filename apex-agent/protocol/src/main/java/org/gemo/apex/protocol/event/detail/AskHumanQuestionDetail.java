package org.gemo.apex.protocol.event.detail;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public final class AskHumanQuestionDetail {
    @JsonProperty("input_type") private String inputType;
    @JsonProperty("question") private String question;
    @JsonProperty("description") private String description;
    @JsonProperty("options") private List<AskHumanOption> options;
}
