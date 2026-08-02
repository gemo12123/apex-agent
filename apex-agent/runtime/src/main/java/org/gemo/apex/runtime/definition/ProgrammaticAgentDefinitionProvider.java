package org.gemo.apex.runtime.definition;
import org.gemo.apex.common.agent.*;import org.gemo.apex.extension.definition.AgentDefinitionProvider;import java.util.*;
public final class ProgrammaticAgentDefinitionProvider implements AgentDefinitionProvider{
 private final Map<String,AgentDefinition> values;
 public ProgrammaticAgentDefinitionProvider(AgentDefinition value){this(List.of(value));}
 public ProgrammaticAgentDefinitionProvider(List<AgentDefinition> input){Map<String,AgentDefinition> m=new LinkedHashMap<>();for(var v:List.copyOf(input))if(m.putIfAbsent(v.metadata().agentKey(),v)!=null)throw new IllegalArgumentException("Agent 名称重复: "+v.metadata().agentKey());if(m.isEmpty())throw new IllegalArgumentException("Agent 定义不能为空");values=Map.copyOf(m);}
 public AgentDefinition load(String key){var v=values.get(key);if(v==null)throw new IllegalArgumentException("Agent 不存在: "+key);return v;}public List<AgentMetadata> listAgents(){return values.values().stream().map(AgentDefinition::metadata).toList();}
}
