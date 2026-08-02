package org.gemo.apex.platform.web;

import org.gemo.apex.platform.security.UserContextFilter;
import org.gemo.apex.protocol.request.ChatRequest;
import org.gemo.apex.protocol.request.RequestType;
import org.gemo.apex.runtime.execution.SessionBusyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/sse")
public class ChatController {
    private final ChatService service;
    public ChatController(ChatService service) { this.service = service; }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequest request,
                           @RequestHeader(UserContextFilter.HEADER) String userId) {
        validate(request);
        return service.chat(request, userId.trim());
    }

    @ExceptionHandler(SessionBusyException.class)
    ResponseEntity<Map<String, Object>> busy(SessionBusyException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("code", 409, "data", Map.of(), "message", "session busy"));
    }

    private static void validate(ChatRequest request) {
        if (request == null || !StringUtils.hasText(request.getSessionId())
                || !StringUtils.hasText(request.getAgentKey()) || request.getType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求字段不完整");
        }
        if (request.getType() == RequestType.NEW && !StringUtils.hasText(request.getQuery())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "NEW query 不能为空");
        }
        if (request.getType() == RequestType.HUMAN_RESPONSE
                && (request.getHumanResponse() == null || request.getHumanResponse().isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "HUMAN_RESPONSE humanResponse 不能为空");
        }
    }
}
