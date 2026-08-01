package org.gemo.apex.hook.lifecycle;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class AgentHookContext {
    private HookPoint hookPoint;
    private AgentRuntimeContext runtimeContext;
    private String hookBean;
    private int order;
    private Map<String, Object> hookOptions;
}
