package org.gemo.apex.protocol.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = MessageFields.EVENT_TYPE)
@JsonSubTypes({
    @JsonSubTypes.Type(value = StreamThinkMessage.class, name = AgentEventType.STREAM_THINK),
    @JsonSubTypes.Type(value = StreamContentMessage.class, name = AgentEventType.STREAM_CONTENT),
    @JsonSubTypes.Type(
            value = InvocationDeclaredMessage.class,
            name = AgentEventType.INVOCATION_DECLARED),
    @JsonSubTypes.Type(
            value = InvocationChangeMessage.class,
            name = AgentEventType.INVOCATION_CHANGE),
    @JsonSubTypes.Type(value = TaskErrorMessage.class, name = AgentEventType.TASK_ERROR),
    @JsonSubTypes.Type(
            value = ArtifactDeclaredMessage.class,
            name = AgentEventType.ARTIFACT_DECLARED),
    @JsonSubTypes.Type(value = ArtifactChangeMessage.class, name = AgentEventType.ARTIFACT_CHANGE),
    @JsonSubTypes.Type(value = EndMessage.class, name = AgentEventType.END),
    @JsonSubTypes.Type(
            value = HumanInterventionMessage.class,
            name = AgentEventType.HUMAN_INTERVENTION)
})
public abstract class AgentMessage {
    @JsonIgnore private String eventType;

    @JsonProperty("context")
    private Map<String, Object> context;
}
