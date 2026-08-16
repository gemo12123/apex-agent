package org.gemo.apex.core.lifecycle;

import org.gemo.apex.common.intervention.HumanInterventionRequest;
import org.gemo.apex.common.tool.ToolResult;

public sealed interface LifecycleDispatchOutcome {
    record Continued() implements LifecycleDispatchOutcome {}

    /** 当前可选子流程因 Hook 修改前置状态而不再需要继续。 */
    record Bypassed() implements LifecycleDispatchOutcome {}

    record EndTurn(String reason) implements LifecycleDispatchOutcome {}

    record BlockTool(String reason) implements LifecycleDispatchOutcome {}

    record DirectToolResult(ToolResult result) implements LifecycleDispatchOutcome {}

    record HumanIntervention(HumanInterventionRequest request)
            implements LifecycleDispatchOutcome {}
}
