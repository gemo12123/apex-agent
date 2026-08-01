package org.gemo.apex.common.execution;

import java.time.Instant;

import static org.gemo.apex.common.support.DomainValues.nonNull;

public record Turn(long turnNo, TurnStatus status, Instant startedTime, Instant endedTime) {
    public Turn {
        if (turnNo < 1) throw new IllegalArgumentException("turnNo 必须大于 0");
        status = nonNull(status, "status");
        startedTime = nonNull(startedTime, "startedTime");
    }
}
