package org.gemo.apex.protocol.event.detail;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
public class AskHumanOption {
    @JsonProperty("label") private String label;
    @JsonProperty("description") private String description;
}
