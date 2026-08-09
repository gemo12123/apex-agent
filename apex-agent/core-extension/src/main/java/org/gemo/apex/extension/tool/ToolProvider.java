package org.gemo.apex.extension.tool;

import java.util.List;
import org.gemo.apex.common.agent.AgentDefinition;

public interface ToolProvider {
    /** 为当前模板装配后的候选定义解析健康工具。 */
    List<AgentTool> loadTools(AgentDefinition definition);
}
