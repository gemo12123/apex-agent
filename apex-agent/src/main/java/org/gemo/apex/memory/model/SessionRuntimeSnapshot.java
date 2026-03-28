package org.gemo.apex.memory.model;

import lombok.Data;
import org.gemo.apex.domain.Plan;

import java.util.Map;

/**
 * 会话运行态快照。
 */
@Data
public class SessionRuntimeSnapshot {

    /**
     * 当前阶段任务标识。
     */
    private String currentStageId;

    /**
     * 计划对象。
     */
    private Plan plan;

    /**
     * 人工恢复结果。
     */
    private Map<String, Object> pendingToolResult;
}
