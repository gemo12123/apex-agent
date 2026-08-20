package org.gemo.apex.platform.web;

import java.util.List;
import org.gemo.apex.platform.security.UserContextFilter;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sse/sessions")
public class ConversationHistoryController {
    private final ConversationHistoryQueryService service;

    public ConversationHistoryController(ConversationHistoryQueryService service) {
        this.service = service;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<List<SessionHistorySummary>> list(@RequestHeader(UserContextFilter.HEADER) String userId) {
        return ApiResponse.success(service.list(userId.trim()));
    }

    @GetMapping(value = "/{sessionId}/history", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<ConversationHistoryView> history(
            @PathVariable String sessionId, @RequestHeader(UserContextFilter.HEADER) String userId) {
        return ApiResponse.success(service.history(sessionId, userId.trim()));
    }
}
