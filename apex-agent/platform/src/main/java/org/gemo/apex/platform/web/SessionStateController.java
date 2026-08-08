package org.gemo.apex.platform.web;

import org.gemo.apex.platform.security.UserContextFilter;
import org.gemo.apex.protocol.request.SessionStateView;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/sse/sessions")
public class SessionStateController {
    private final SessionStateQueryService service;

    public SessionStateController(SessionStateQueryService service) {
        this.service = service;
    }

    @GetMapping(value = "/{sessionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<SessionStateView> state(
            @PathVariable String sessionId,
            @RequestParam String agentKey,
            @RequestHeader(UserContextFilter.HEADER) String userId) {
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(agentKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId/agentKey 不能为空");
        }
        return ApiResponse.success(service.query(sessionId, agentKey, userId.trim()));
    }
}
