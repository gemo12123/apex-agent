package org.gemo.apex.core;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 同一 session 在单实例内只允许一个执行流程进行中。
 */
@Component
public class SessionExecutionGuard {

    private final Set<String> runningSessionIds = ConcurrentHashMap.newKeySet();

    public boolean tryAcquire(String sessionId) {
        return sessionId != null && runningSessionIds.add(sessionId);
    }

    public void release(String sessionId) {
        if (sessionId != null) {
            runningSessionIds.remove(sessionId);
        }
    }
}
