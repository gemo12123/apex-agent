package org.gemo.apex.hook.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ToolConfirmationDisplayField {
    @JsonProperty("key")
    private String key;

    @JsonProperty("label")
    private String label;

    @JsonProperty("value")
    private Object value;

    @JsonProperty("type")
    private String type;
}
