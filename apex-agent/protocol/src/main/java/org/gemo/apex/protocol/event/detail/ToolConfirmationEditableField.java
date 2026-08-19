package org.gemo.apex.protocol.event.detail;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolConfirmationEditableField {
    @JsonProperty("key")
    private String key;

    @JsonProperty("label")
    private String label;

    @JsonProperty("input_type")
    private EditableFieldInputType inputType;

    @JsonProperty("value")
    private Object value;

    @JsonProperty("required")
    private Boolean required;

    @JsonProperty("options")
    private List<Map<String, Object>> options;
}
