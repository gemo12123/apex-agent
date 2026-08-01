package org.gemo.apex.protocol.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data @EqualsAndHashCode(callSuper = true) @SuperBuilder @NoArgsConstructor
public class ArtifactDeclaredMessage extends AgentMessage {
    @JsonProperty("messages") private List<ArtifactDetail> messages;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ArtifactDetail {
        @JsonProperty("scope") private String scope;
        @JsonProperty("data_type") private String dataType;
        @JsonProperty("source") private String source;
        @JsonProperty("artifact_id") private String artifactId;
        @JsonProperty("artifact_name") private String artifactName;
        @JsonProperty("artifact_type") private String artifactType;
        @JsonProperty("content") private String content;
        @JsonProperty("complete") private boolean complete;
    }
}
