package org.gemo.apex.common.shared;

/** Session 共享数据的自动清理时机。 */
public enum SharedDataCleanupPolicy {
    ITERATION_END,
    TURN_END,
    NEVER
}
