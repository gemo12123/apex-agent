package org.gemo.apex.constant;

/**
 * 执行状态枚举
 * 用于标识当前会话的整体执行状态
 */
public enum ExecutionStatus {
    /**
     * 执行中
     */
    IN_PROGRESS,

    /**
     * 已完成
     */
    COMPLETED,

    /**
     * 执行失败
     */
    FAILED,

    /**
     * 等待人工输入（Human in the Loop）
     */
    HUMAN_IN_THE_LOOP
}
