package org.gemo.apex.tool;

import org.gemo.apex.constant.ToolContextKeys;
import org.gemo.apex.constant.ToolNames;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.memory.search.SessionSearchQuery;
import org.gemo.apex.memory.search.SessionSearchResult;
import org.gemo.apex.memory.search.SessionSearchScope;
import org.gemo.apex.memory.search.SessionSearchService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class SessionSearchTool {

    private final SessionSearchService sessionSearchService;

    public SessionSearchTool(SessionSearchService sessionSearchService) {
        this.sessionSearchService = sessionSearchService;
    }

    @Tool(name = ToolNames.SESSION_SEARCH, description = "Search persisted dialogue messages and summaries from prior session history.")
    public SessionSearchResult session_search(SessionSearchQuery query, ToolContext toolContext) {
        SuperAgentContext context = (SuperAgentContext) toolContext.getContext().get(ToolContextKeys.SESSION_CONTEXT);
        SessionSearchScope scope = new SessionSearchScope(
                context != null ? context.getUserId() : null,
                context != null ? context.getAgentKey() : null);
        return sessionSearchService.search(query, scope);
    }
}
