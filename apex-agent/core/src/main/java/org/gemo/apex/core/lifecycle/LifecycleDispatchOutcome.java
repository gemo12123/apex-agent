package org.gemo.apex.core.lifecycle;

import org.gemo.apex.common.intervention.HumanInterventionRequest;
import org.gemo.apex.common.tool.ToolResult;

public sealed interface LifecycleDispatchOutcome {
    record Continued() implements LifecycleDispatchOutcome {}

    record EndTurn(String reason) implements LifecycleDispatchOutcome {}

    record BlockTool(String reason) implements LifecycleDispatchOutcome {}

    record DirectToolResult(ToolResult result) implements LifecycleDispatchOutcome {}

    record HumanIntervention(HumanInterventionRequest request)
            implements LifecycleDispatchOutcome {}
}
