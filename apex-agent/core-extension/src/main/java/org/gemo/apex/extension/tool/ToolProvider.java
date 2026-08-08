package org.gemo.apex.extension.tool;

import java.util.List;
import org.gemo.apex.common.agent.AgentDefinition;
import org.gemo.apex.common.agent.AgentDefinitionRecoverySnapshot;

public interface ToolProvider {
    /** 为 NEW 构造后的候选定义解析健康工具。 */
    List<AgentTool> loadTools(AgentDefinition definition);

    /** 为恢复投影解析健康工具；工具实例不得进入快照。 */
    List<AgentTool> loadTools(AgentDefinitionRecoverySnapshot definition);
}
