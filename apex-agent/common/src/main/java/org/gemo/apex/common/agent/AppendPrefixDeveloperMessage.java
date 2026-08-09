package org.gemo.apex.common.agent;

import static org.gemo.apex.common.support.DomainValues.nonNull;

public record AppendPrefixDeveloperMessage(PrefixDeveloperMessage message)
        implements AgentDefinitionOperation {
    public AppendPrefixDeveloperMessage {
        message = nonNull(message, "message");
    }
}
