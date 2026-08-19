package org.gemo.apex.common.snapshot;

import static org.gemo.apex.common.support.DomainValues.nonNull;

import java.time.Instant;
import org.gemo.apex.common.execution.TurnStatus;

public record TurnSnapshot(
        long turnNo,
        TurnStatus status,
        IterationSnapshot currentIteration,
        Instant startedTime,
        Instant endedTime) {
    public TurnSnapshot {
        if (turnNo < 1) {
            throw new IllegalArgumentException("turnNo 必须大于 0");
        }
        status = nonNull(status, "status");
        startedTime = nonNull(startedTime, "startedTime");
    }
}
