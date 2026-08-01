package org.gemo.apex.common.hook.result;

import org.gemo.apex.common.agent.AgentDefinitionOperation;

import java.util.List;

import static org.gemo.apex.common.support.DomainValues.immutableList;

public record ContinueAgentBuild(List<AgentDefinitionOperation> operations) implements AgentBuildHookResult {
    public ContinueAgentBuild { operations = immutableList(operations, "operations"); }
}
