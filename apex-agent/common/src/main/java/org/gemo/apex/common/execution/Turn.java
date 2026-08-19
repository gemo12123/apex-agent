package org.gemo.apex.common.execution;

import static org.gemo.apex.common.support.DomainValues.nonNull;

import java.time.Instant;

public record Turn(long turnNo, TurnStatus status, Instant startedTime, Instant endedTime) {
    public Turn {
        if (turnNo < 1) {
            throw new IllegalArgumentException("turnNo 必须大于 0");
        }
        status = nonNull(status, "status");
        startedTime = nonNull(startedTime, "startedTime");
    }
}
