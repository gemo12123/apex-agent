package org.gemo.apex.protocol.event.detail;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolConfirmationDisplayField {
    @JsonProperty("key") private String key;
    @JsonProperty("label") private String label;
    @JsonProperty("value") private Object value;
    @JsonProperty("type") private String type;
}
