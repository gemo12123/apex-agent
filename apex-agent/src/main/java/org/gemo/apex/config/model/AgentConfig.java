package org.gemo.apex.config.model;

import lombok.Data;
import org.gemo.apex.constant.ModeEnum;
import java.util.List;

/**
 * Global configuration model for an Agent
 */
@Data
public class AgentConfig {
    /** Unique identifier for the agent */
    private String agentKey;

    /** Agent display name */
    private String name;

    /** Agent description (used by LLMs as tool description) */
    private String description;

    /** HTTP Endpoint URL for the sub-agent if it runs remotely */
    private String url;

    /** Timeout in milliseconds, default 60000 */
    private Integer timeout = 60000;

    /** Workspace path (e.g. classpath:agents/coder/) */
    private String workspace;

    /** List of MCP tools this agent is allowed to use */
    private List<String> mcps;

    /** List of SubAgents this agent is allowed to query */
    private List<String> subAgents;

    /** List of Skills this agent is allowed to use */
    private List<String> skills;

    /** Default execution mode for new conversations */
    private ModeEnum defaultExecutionMode;
}
