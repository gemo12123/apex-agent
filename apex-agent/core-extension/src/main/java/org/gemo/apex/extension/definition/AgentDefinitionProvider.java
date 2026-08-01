package org.gemo.apex.extension.definition;

import org.gemo.apex.common.agent.AgentDefinition;
import org.gemo.apex.common.agent.AgentMetadata;

import java.util.List;

public interface AgentDefinitionProvider {
    AgentDefinition load(String agentKey);

    /**
     * 直接加载轻量元数据，不得通过逐个加载完整定义实现。
     */
    List<AgentMetadata> listAgents();
}
