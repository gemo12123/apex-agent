package org.gemo.apex.core.engine;

import lombok.Getter;

@Getter
public class ToolExecutionOutcome {

    private boolean succeeded = true;
    private String error;

    public void markFailed(String error) {
        this.succeeded = false;
        this.error = error;
    }
}
