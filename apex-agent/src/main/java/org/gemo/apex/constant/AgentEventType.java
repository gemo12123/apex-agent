package org.gemo.apex.constant;

/**
 * Agent 消息事件类型常量。
 * <p>
 * 这里刻意使用字符串常量而不是 enum，因为这些值会被用于
 * {@code @JsonSubTypes.Type(name = ...)} 之类的注解属性，而注解属性要求编译期常量。
 * 如果改成 enum，诸如 {@code AgentEventType.STREAM_THINK.name()} 这类写法不能用于注解参数。
 */
public final class AgentEventType {

    public static final String STREAM_THINK = "STREAM_THINK";
    public static final String STREAM_CONTENT = "STREAM_CONTENT";
    public static final String PLAN_DECLARED = "PLAN_DECLARED";
    public static final String PLAN_CHANGE = "PLAN_CHANGE";
    public static final String INVOCATION_DECLARED = "INVOCATION_DECLARED";
    public static final String INVOCATION_CHANGE = "INVOCATION_CHANGE";
    public static final String TASK_THINK_DECLARED = "TASK_THINK_DECLARED";
    public static final String TASK_THINK_CHANGE = "TASK_THINK_CHANGE";
    public static final String ARTIFACT_DECLARED = "ARTIFACT_DECLARED";
    public static final String ARTIFACT_CHANGE = "ARTIFACT_CHANGE";
    public static final String END = "END";
    public static final String ASK_HUMAN = "ASK_HUMAN";

    private AgentEventType() {
    }
}
