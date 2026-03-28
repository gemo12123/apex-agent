package org.gemo.apex.constant;

/**
 * 运行模式枚举
 */
public enum ModeEnum {
    PLAN_EXECUTOR("plan-executor"),
    REACT("react");

    private final String mode;

    ModeEnum(String mode) {
        this.mode = mode;
    }

    public String getMode() {
        return mode;
    }

    /**
     * 将 LLM 传入的模式字符串（如 "PlanExecutor"、"ReAct"）标准化为枚举值。
     * 支持不区分大小写匹配。
     */
    public static ModeEnum fromLlmValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("mode 值不能为空");
        }
        for (ModeEnum m : values()) {
            if (m.name().equalsIgnoreCase(value)
                    || m.name().replace("_", "").equalsIgnoreCase(value)) {
                return m;
            }
        }
        throw new IllegalArgumentException("未知的执行模式: " + value);
    }
}
