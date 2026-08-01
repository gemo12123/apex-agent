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
public class ArtifactChangeMessage extends AgentMessage {
    @JsonProperty("messages") private List<ArtifactChangeDetail> messages;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ArtifactChangeDetail {
        @JsonProperty("scope") private String scope;
        @JsonProperty("change_type") private String changeType;
        @JsonProperty("source") private String source;
        @JsonProperty("artifact_id") private String artifactId;
        @JsonProperty("artifact_name") private String artifactName;
        @JsonProperty("artifact_type") private String artifactType;
        @JsonProperty("content") private String content;
    }
}
