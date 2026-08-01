package org.gemo.apex.definition.agent;

import lombok.Data;
import org.gemo.apex.config.model.AgentHooksConfig;
import org.gemo.apex.constant.ModeEnum;

import java.util.Collections;
import java.util.List;

@Data
public class AgentWorkspaceConfig {

    private List<String> allowMcps = Collections.emptyList();
    private List<String> allowSubAgents = Collections.emptyList();
    private List<String> allowSkills = Collections.emptyList();
    private ModeEnum defaultExecutionMode;
    private boolean hooksConfigured = false;
    private AgentHooksConfig hooks = AgentHooksConfig.empty();
}
