package org.gemo.apex.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.config.ApexGlobalProperties;
import org.gemo.apex.config.model.AgentConfig;
import org.gemo.apex.domain.dto.ChatRequest;
import org.gemo.apex.web.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SSE 控制器。
 */
@Slf4j
@RestController
@RequestMapping("/api/sse")
public class ChatController {

    @Autowired
    private ApexGlobalProperties apexGlobalProperties;

    @Autowired
    private ChatService chatService;

    @GetMapping(value = "/agents", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getAgents() {
        List<Map<String, String>> data = (apexGlobalProperties.getAgents() == null
                ? List.<AgentConfig>of()
                : apexGlobalProperties.getAgents().values().stream().toList()).stream()
                .map(agent -> {
                    Map<String, String> map = new HashMap<>();
                    map.put("agentKey", agent.getAgentKey());
                    map.put("name", agent.getName());
                    return map;
                })
                .toList();
        return Map.of("code", 200, "data", data, "message", "success");
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter executeWithSse(@RequestBody ChatRequest request) {
        String sessionId = request.getSessionId();
        if (!StringUtils.hasText(sessionId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId must not be blank");
        }
        return chatService.chat(request);
    }
}
