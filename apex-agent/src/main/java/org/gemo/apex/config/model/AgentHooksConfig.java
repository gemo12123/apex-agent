package org.gemo.apex.config.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentHooksConfig {

    @Builder.Default
    private List<HookBindingConfig> turnStart = new ArrayList<>();

    @Builder.Default
    private List<HookBindingConfig> traceStart = new ArrayList<>();

    @Builder.Default
    private List<HookBindingConfig> preModelCall = new ArrayList<>();

    @Builder.Default
    private List<HookBindingConfig> postModelCall = new ArrayList<>();

    @Builder.Default
    private List<HookBindingConfig> preToolCall = new ArrayList<>();

    @Builder.Default
    private List<HookBindingConfig> postToolCall = new ArrayList<>();

    @Builder.Default
    private List<HookBindingConfig> traceEnd = new ArrayList<>();

    @Builder.Default
    private List<HookBindingConfig> turnEnd = new ArrayList<>();

    @Builder.Default
    private boolean disabled = false;

    public static AgentHooksConfig disabled() {
        return AgentHooksConfig.builder().disabled(true).build();
    }

    public static AgentHooksConfig empty() {
        return AgentHooksConfig.builder().build();
    }
}
