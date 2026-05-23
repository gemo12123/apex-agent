package org.gemo.apex.constant;

/**
 * @author gemo
 * @date 2026/1/18 21:12
 */
public enum ContextKeyEnum {
    MODE("mode"),
    STAGE_ID("stage_id"),
    TASK_ID("task_id"),
    INVOCATION_ID("invocation_id"),
    ARTIFACT_ID("artifact_id"),
    EXECUTOR("executor"),
    CONTENT_ID("content_id"),
    EXECUTION_STATUS("execution_status"),
    ERROR_CODE("error_code"),
    ERROR_MESSAGE("error_message");

    private final String key;

    ContextKeyEnum(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
