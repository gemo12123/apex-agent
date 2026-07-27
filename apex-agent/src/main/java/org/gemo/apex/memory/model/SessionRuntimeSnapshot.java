package org.gemo.apex.memory.model;

import lombok.Data;
import org.gemo.apex.domain.Plan;
import org.gemo.apex.domain.interaction.PendingHumanInteraction;
import org.gemo.apex.domain.interaction.PendingToolExecution;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;

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

    private PendingHumanInteraction pendingHumanInteraction;

    private PendingToolExecution pendingToolExecution;

    private Long turnNo;

    private Integer iterationNo;

    private String workingMessagesPayload;

    private List<String> activeSkillNames = new ArrayList<>();

    private List<String> enabledToolNames = new ArrayList<>();
}
