package org.gemo.apex.platform.web;

import java.util.Map;
import org.gemo.apex.extension.definition.AgentDefinitionProvider;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sse")
public class AgentController {
    private final AgentDefinitionProvider definitions;

    public AgentController(AgentDefinitionProvider definitions) {
        this.definitions = definitions;
    }

    @GetMapping(value = "/agents", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<?> agents() {
        return ApiResponse.success(
                definitions.listAgents().stream()
                        .map(value -> Map.of("agentKey", value.agentKey(), "name", value.name()))
                        .toList());
    }
}
