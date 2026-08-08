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

/**
 * SSE 聊天接口的 HTTP 边界。
 *
 * <p>这里只校验传输层必填字段；会话所有权、状态和恢复合法性由 core 在创建 Agent 时校验。</p>
 */
@RestController
@RequestMapping("/api/sse")
public class ChatController {
    private final ChatService service;
    public ChatController(ChatService service) { this.service = service; }

    /** 接收新请求或人工恢复请求，并交由服务创建对应 SSE 执行。 */
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

    /** 根据请求类型校验互斥的业务载荷，防止无效请求进入异步执行链路。 */
    private static void validate(ChatRequest request) {
        if (request == null || !StringUtils.hasText(request.getSessionId())
                || !StringUtils.hasText(request.getAgentKey()) || request.getType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求字段不完整");
        }
        if (request.getType() == RequestType.NEW && !StringUtils.hasText(request.getQuery())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "NEW query 不能为空");
        }
        if (request.getType() == RequestType.HUMAN_RESPONSE && request.getHumanResponse() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "HUMAN_RESPONSE humanResponse 不能缺失");
        }
    }
}
