package org.gemo.apex.domain.interaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingHumanInteraction {
    private String interactionType;
    private String toolCallId;
    private String invocationId;
    private String confirmationId;
}
