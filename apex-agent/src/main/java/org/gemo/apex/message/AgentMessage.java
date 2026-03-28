package org.gemo.apex.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.gemo.apex.constant.AgentEventType;
import org.gemo.apex.constant.MessageFields;

import java.util.Map;

/**
 * Agent消息基类
 * 基于event_type进行多态反序列化
 */
@Data
@SuperBuilder
@NoArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = MessageFields.EVENT_TYPE)
@JsonSubTypes({
                @JsonSubTypes.Type(value = StreamThinkMessage.class, name = AgentEventType.STREAM_THINK),
                @JsonSubTypes.Type(value = StreamContentMessage.class, name = AgentEventType.STREAM_CONTENT),
                @JsonSubTypes.Type(value = PlanDeclaredMessage.class, name = AgentEventType.PLAN_DECLARED),
                @JsonSubTypes.Type(value = PlanChangeMessage.class, name = AgentEventType.PLAN_CHANGE),
                @JsonSubTypes.Type(value = InvocationDeclaredMessage.class, name = AgentEventType.INVOCATION_DECLARED),
                @JsonSubTypes.Type(value = InvocationChangeMessage.class, name = AgentEventType.INVOCATION_CHANGE),
                @JsonSubTypes.Type(value = TaskThinkDeclaredMessage.class, name = AgentEventType.TASK_THINK_DECLARED),
                @JsonSubTypes.Type(value = TaskThinkChangeMessage.class, name = AgentEventType.TASK_THINK_CHANGE),
                @JsonSubTypes.Type(value = ArtifactDeclaredMessage.class, name = AgentEventType.ARTIFACT_DECLARED),
                @JsonSubTypes.Type(value = ArtifactChangeMessage.class, name = AgentEventType.ARTIFACT_CHANGE),
                @JsonSubTypes.Type(value = EndMessage.class, name = AgentEventType.END),
                @JsonSubTypes.Type(value = AskHumanMessage.class, name = AgentEventType.ASK_HUMAN)
})
public abstract class AgentMessage {
        /**
         * 事件类型（序列化时忽略，由 @JsonTypeInfo 机制自动控制 event_type 的输出）
         */
        @JsonIgnore
        private String eventType;

        /**
         * 上下文信息，如智能体模型、阶段ID、invocation_id等
         */
        @JsonProperty("context")
        private Map<String, Object> context;
}
