package org.gemo.apex.common.snapshot;

import org.gemo.apex.common.execution.IterationStatus;
import org.gemo.apex.common.model.ModelRequest;
import org.gemo.apex.common.model.ModelResponse;
import org.gemo.apex.common.tool.ToolResult;

import java.time.Instant;
import java.util.List;

import static org.gemo.apex.common.support.DomainValues.*;

public record IterationSnapshot(int iterationNo, IterationStatus status, ModelRequest modelRequest,
                                ModelResponse modelResponse, List<ToolResult> completedToolResults,
                                Instant startedTime, Instant endedTime) {
    public IterationSnapshot {
        if (iterationNo < 1) {
            throw new IllegalArgumentException("iterationNo 必须大于 0");
        }
        status = nonNull(status, "status");
        completedToolResults = immutableList(completedToolResults, "completedToolResults");
        startedTime = nonNull(startedTime, "startedTime");
    }
}
