package org.gemo.apex.common.hook.result;

import static org.gemo.apex.common.support.DomainValues.immutableList;

import java.util.List;
import org.gemo.apex.common.agent.AgentDefinitionOperation;

public record ContinueAgentBuild(List<AgentDefinitionOperation> operations)
        implements AgentBuildHookResult {
    public ContinueAgentBuild {
        operations = immutableList(operations, "operations");
    }
}
