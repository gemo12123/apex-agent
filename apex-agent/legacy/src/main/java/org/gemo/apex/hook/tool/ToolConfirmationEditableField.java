package org.gemo.apex.hook.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
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
