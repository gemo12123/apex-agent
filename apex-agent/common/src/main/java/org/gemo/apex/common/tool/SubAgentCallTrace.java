package org.gemo.apex.common.tool;

import static org.gemo.apex.common.support.DomainValues.*;

import java.util.ArrayList;
import java.util.List;

public record SubAgentCallTrace(String traceId, List<String> agentKeys, int maxDepth) {
    public SubAgentCallTrace {
        traceId = required(traceId, "traceId");
        agentKeys = immutableList(agentKeys, "agentKeys");
        if (agentKeys.isEmpty()) {
            throw new IllegalArgumentException("agentKeys 不能为空");
        }
        if (maxDepth < 1) {
            throw new IllegalArgumentException("maxDepth 必须大于 0");
        }
    }

    public SubAgentCallTrace append(String agentKey) {
        if (agentKeys.size() >= maxDepth) {
            throw new IllegalArgumentException("agentKeys 超过 maxDepth");
        }
        List<String> next = new ArrayList<>(agentKeys);
        next.add(required(agentKey, "agentKey"));
        return new SubAgentCallTrace(traceId, next, maxDepth);
    }
}
