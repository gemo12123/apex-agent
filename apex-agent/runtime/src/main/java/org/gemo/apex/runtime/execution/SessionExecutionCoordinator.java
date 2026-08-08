package org.gemo.apex.runtime.execution;

public interface SessionExecutionCoordinator {
    SessionExecutionLease acquire(String id);
}
