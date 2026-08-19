package org.gemo.apex.common.execution;

import static org.gemo.apex.common.support.DomainValues.nonNull;

import java.time.Instant;

public record Iteration(
        int iterationNo, IterationStatus status, Instant startedTime, Instant endedTime) {
    public Iteration {
        if (iterationNo < 1) {
            throw new IllegalArgumentException("iterationNo 必须大于 0");
        }
        status = nonNull(status, "status");
        startedTime = nonNull(startedTime, "startedTime");
    }
}
