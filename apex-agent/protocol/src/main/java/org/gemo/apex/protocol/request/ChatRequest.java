package org.gemo.apex.protocol.request;

import java.util.Map;
import lombok.Data;

@Data
public class ChatRequest {
    private String query;
    private String sessionId;
    private RequestType type = RequestType.NEW;
    private String agentKey = "default_agent";
    private Map<String, Object> humanResponse;
}
