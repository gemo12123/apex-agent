package org.gemo.apex.core.agent;

import org.gemo.apex.common.agent.AgentDefinitionSnapshot;
import org.gemo.apex.common.snapshot.HistoricalToolBinding;
import org.gemo.apex.core.tool.ToolCatalog;

import java.util.List;
import java.util.Set;

public record AgentAssemblyResult(AgentDefinitionSnapshot definition,
                                  Set<String> effectiveEnabledTools,
                                  List<HistoricalToolBinding> historicalToolBindings,
                                  ToolCatalog toolCatalog) {
    public AgentAssemblyResult {
        effectiveEnabledTools = Set.copyOf(effectiveEnabledTools);
        historicalToolBindings = List.copyOf(historicalToolBindings);
    }
}
