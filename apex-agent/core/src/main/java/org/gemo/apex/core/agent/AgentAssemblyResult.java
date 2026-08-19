package org.gemo.apex.core.agent;

import java.util.List;
import java.util.Set;
import org.gemo.apex.common.agent.AgentDefinitionSnapshot;
import org.gemo.apex.common.snapshot.ExecutionErrorSnapshot;
import org.gemo.apex.common.snapshot.HistoricalToolBinding;
import org.gemo.apex.core.tool.ToolCatalog;

public record AgentAssemblyResult(
        AgentDefinitionSnapshot definition,
        Set<String> effectiveEnabledTools,
        List<HistoricalToolBinding> historicalToolBindings,
        List<ExecutionErrorSnapshot> executionErrors,
        ToolCatalog toolCatalog) {
    public AgentAssemblyResult {
        effectiveEnabledTools = Set.copyOf(effectiveEnabledTools);
        historicalToolBindings = List.copyOf(historicalToolBindings);
        executionErrors = List.copyOf(executionErrors);
    }
}
