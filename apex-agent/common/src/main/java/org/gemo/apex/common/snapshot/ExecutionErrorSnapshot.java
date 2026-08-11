package org.gemo.apex.common.snapshot;

import static org.gemo.apex.common.support.DomainValues.*;

import java.time.Instant;
import org.gemo.apex.common.hook.HookPoint;

public record ExecutionErrorSnapshot(
        long turnNo,
        Integer iterationNo,
        ExecutionErrorType type,
        HookPoint hookPoint,
        String hookId,
        String message,
        Instant occurredTime) {
    public ExecutionErrorSnapshot {
        if (turnNo < 1) {
            throw new IllegalArgumentException("turnNo 必须大于 0");
        }
        if (iterationNo != null && iterationNo < 1) {
            throw new IllegalArgumentException("iterationNo 必须大于 0");
        }
        type = nonNull(type, "type");
        message = required(message, "message");
        occurredTime = nonNull(occurredTime, "occurredTime");
        if (type == ExecutionErrorType.HOOK) {
            hookPoint = nonNull(hookPoint, "hookPoint");
            hookId = required(hookId, "hookId");
        } else if (hookPoint != null || hookId != null) {
            throw new IllegalArgumentException("非 Hook 异常不能携带 hookPoint 或 hookId");
        }
    }
}
