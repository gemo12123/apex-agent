package org.gemo.apex.tool;

import org.gemo.apex.constant.ToolContextKeys;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.memory.search.SessionSearchQuery;
import org.gemo.apex.memory.search.SessionSearchResult;
import org.gemo.apex.memory.search.SessionSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionSearchToolTest {

    @Test
    void sessionSearchShouldDelegateWithScopeFromSessionContext() {
        SessionSearchService service = mock(SessionSearchService.class);
        SessionSearchTool tool = new SessionSearchTool(service);
        SuperAgentContext context = new SuperAgentContext();
        context.setUserId("user-1");
        context.setAgentKey("agent-1");
        SessionSearchQuery query = new SessionSearchQuery("find previous fix", null, 8, "hybrid", true, true);
        SessionSearchResult expected = new SessionSearchResult("find previous fix", java.util.List.of());
        when(service.search(any(), any())).thenReturn(expected);

        SessionSearchResult actual = tool.session_search(query,
                new ToolContext(Map.of(ToolContextKeys.SESSION_CONTEXT, context)));

        assertSame(expected, actual);
        verify(service).search(any(), any());
    }
}
