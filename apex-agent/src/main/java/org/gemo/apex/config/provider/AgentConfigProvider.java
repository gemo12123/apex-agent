package org.gemo.apex.config.provider;

import org.gemo.apex.config.model.AgentConfig;
import java.util.List;

/**
 * Provider interface for Agent configurations.
 * Allows decoupling configuration reading from the properties file,
 * supporting future database or dynamic configuration sources.
 */
public interface AgentConfigProvider {

    /**
     * Get the global configuration for a specific agent.
     * 
     * @param agentKey the unique identifier for the agent
     * @return the configuration, or null if not found
     */
    AgentConfig getAgentConfig(String agentKey);

    /**
     * Get a list of all configured agents.
     * 
     * @return list of agent configurations
     */
    List<AgentConfig> getAllAgents();
}
