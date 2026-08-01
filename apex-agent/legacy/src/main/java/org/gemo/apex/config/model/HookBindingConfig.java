package org.gemo.apex.config.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HookBindingConfig {

    private String bean;

    @Builder.Default
    private boolean enabled = true;

    @Builder.Default
    private int order = 0;

    @Builder.Default
    private List<String> tools = List.of("*");

    @Builder.Default
    private Map<String, Object> options = Map.of();
}
