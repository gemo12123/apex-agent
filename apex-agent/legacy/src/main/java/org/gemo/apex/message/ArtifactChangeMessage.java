package org.gemo.apex.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * ARTIFACT_CHANGE - 产物变更消息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class ArtifactChangeMessage extends AgentMessage {

    /**
     * 变更信息列表
     */
    @JsonProperty("messages")
    protected List<ArtifactChangeDetail> messages;

    @lombok.Data
    @Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ArtifactChangeDetail {
        /**
         * 作用域：STAGE, GLOBAL
         */
        @JsonProperty("scope")
        private String scope;

        /**
         * 变更类型：CONTENT_APPEND
         */
        @JsonProperty("change_type")
        private String changeType;

        /**
         * 来源，如：知识库
         */
        @JsonProperty("source")
        private String source;

        /**
         * 文件ID
         */
        @JsonProperty("artifact_id")
        private String artifactId;

        /**
         * 文件名
         */
        @JsonProperty("artifact_name")
        private String artifactName;

        /**
         * 文件类型
         */
        @JsonProperty("artifact_type")
        private String artifactType;

        /**
         * 内容，可能是文字、链接、ID等
         */
        @JsonProperty("content")
        private String content;
    }
}
