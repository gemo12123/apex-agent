package org.gemo.apex.protocol.request;

import lombok.Data;

import java.util.Map;

@Data
public class ChatRequest {
    private String query;
    private String sessionId;
    private RequestType type = RequestType.NEW;
    private String agentKey = "default_agent";
    private Map<String, Object> humanResponse;
}
