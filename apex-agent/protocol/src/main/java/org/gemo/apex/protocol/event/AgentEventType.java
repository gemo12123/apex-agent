package org.gemo.apex.protocol.event;

public final class AgentEventType {
    public static final String STREAM_THINK = "STREAM_THINK";
    public static final String STREAM_CONTENT = "STREAM_CONTENT";
    public static final String PLAN_DECLARED = "PLAN_DECLARED";
    public static final String PLAN_CHANGE = "PLAN_CHANGE";
    public static final String INVOCATION_DECLARED = "INVOCATION_DECLARED";
    public static final String INVOCATION_CHANGE = "INVOCATION_CHANGE";
    public static final String TASK_THINK_DECLARED = "TASK_THINK_DECLARED";
    public static final String TASK_THINK_CHANGE = "TASK_THINK_CHANGE";
    public static final String TASK_ERROR = "TASK_ERROR";
    public static final String ARTIFACT_DECLARED = "ARTIFACT_DECLARED";
    public static final String ARTIFACT_CHANGE = "ARTIFACT_CHANGE";
    public static final String END = "END";
    public static final String HUMAN_INTERVENTION = "HUMAN_INTERVENTION";

    private AgentEventType() {}
}
