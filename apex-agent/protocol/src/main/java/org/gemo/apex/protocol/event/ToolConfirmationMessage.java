package org.gemo.apex.protocol.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.gemo.apex.protocol.event.detail.ToolConfirmationDetail;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class ToolConfirmationMessage extends AgentMessage {
    @JsonProperty("messages") private List<ToolConfirmationDetail> messages;
}
