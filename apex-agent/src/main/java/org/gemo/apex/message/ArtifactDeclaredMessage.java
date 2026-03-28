package org.gemo.apex.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * ARTIFACT - 产物消息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class ArtifactDeclaredMessage extends AgentMessage {

    /**
     * 产物信息列表
     */
    @JsonProperty("messages")
    protected List<ArtifactDetail> messages;

    @lombok.Data
    @Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ArtifactDetail {
        /**
         * 作用域：STAGE, GLOBAL
         */
        @JsonProperty("scope")
        private String scope;

        @JsonProperty("data_type")
        private String dataType;

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

        /**
         * 该阶段是否直接结束，默认false
         */
        @JsonProperty("complete")
        private boolean complete;
    }
}
