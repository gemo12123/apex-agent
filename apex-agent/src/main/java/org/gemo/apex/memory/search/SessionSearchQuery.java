package org.gemo.apex.memory.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionSearchQuery {
    private String query;
    private String sessionId;
    private Integer limit;
    private String searchMode;
    private Boolean includeSummaries;
    private Boolean includeMessages;
}
