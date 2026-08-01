package org.gemo.apex.tool;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.constant.ChangeType;
import org.gemo.apex.constant.ContextKeyEnum;
import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.constant.PlanOperationType;
import org.gemo.apex.constant.TaskStatus;
import org.gemo.apex.constant.ToolContextKeys;
import org.gemo.apex.constant.ToolNames;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.domain.Plan;
import org.gemo.apex.domain.Stage;
import org.apache.commons.lang3.StringUtils;
import org.gemo.apex.message.PlanChangeMessage;
import org.gemo.apex.util.MessageUtils;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PlanExecutor 模式节点操作：状态回写与计划微调
 */
@Slf4j
public class UpdatePlanTool {

    @Tool(name = ToolNames.UPDATE_PLAN, description = "当当前阶段需要开始或无法完成，或者发现后续计划不合理时，使用该工具修改计划。支持开始、完成、添加、删除、更新阶段。若需要串联动作（如完成当前并开始下一个），可通过一次调用传入多个操作。")
    public String update_plan(Request request, ToolContext toolContext) {

        SuperAgentContext context = getContext(toolContext);
        if (context == null) {
            return "错误：无法获取会话上下文，修改计划失败。";
        }

        Plan plan = context.getPlan();
        if (plan == null || plan.getStages() == null) {
            return "错误：当前会话尚未制定有效的执行计划（Plan 为空），无法修改。";
        }

        if (request == null || request.operations() == null || request.operations().isEmpty()) {
            return "错误：未传入任何操作。";
        }

        List<PlanChangeMessage.PlanChangeDetail> changeDetails = new ArrayList<>();
        StringBuilder resultMessage = new StringBuilder("计划更新成功。执行影响如下：\n");

        synchronized (plan) {
            List<Stage> stages = plan.getStages();

            for (Operation op : request.operations()) {
                String operation = op.operation();
                String targetStageId = op.targetStageId();
                String stageName = op.stageName();
                String description = op.description();
                PlanOperationType operationType;

                try {
                    operationType = PlanOperationType.valueOf(operation);
                } catch (IllegalArgumentException | NullPointerException e) {
                    return "错误：不支持的操作类型 " + operation;
                }

                int currentStageIndex = -1;
                String currentStageId = context.getCurrentStageId();
                if (currentStageId != null) {
                    for (int i = 0; i < stages.size(); i++) {
                        if (stages.get(i).getId().equals(currentStageId)) {
                            currentStageIndex = i;
                            break;
                        }
                    }
                }

                switch (operationType) {
                    case START_CURRENT:
                        Stage startStage = null;
                        for (Stage stage : stages) {
                            if (stage.getId().equals(targetStageId)) {
                                startStage = stage;
                                break;
                            }
                        }
                        if (startStage == null) {
                            return "错误：未找到ID为 " + targetStageId + " 的阶段。";
                        }
                        if (startStage.getStatus() != TaskStatus.PENDING) {
                            return "错误：阶段 " + targetStageId + " 不是 PENDING 状态，无法开始。";
                        }
                        startStage.setStatus(TaskStatus.RUNNING);
                        context.setCurrentStageId(targetStageId);
                        log.info("会话 [{}] 开始了阶段: {}", context.getSessionId(), targetStageId);
                        resultMessage.append("- 成功开始阶段：[").append(startStage.getName()).append("] (ID: ")
                                .append(targetStageId).append(")\n");

                        changeDetails.add(PlanChangeMessage.PlanChangeDetail.builder()
                                .changeType(ChangeType.STATUS_CHANGE.name())
                                .stageId(targetStageId)
                                .status(TaskStatus.RUNNING.name())
                                .build());
                        break;

                    case ADD_STAGE:
                        Stage newStage = new Stage();
                        if (StringUtils.isNotEmpty(stageName))
                            newStage.setName(stageName);
                        if (StringUtils.isNotEmpty(description))
                            newStage.setDescription(description);
                        newStage.setStatus(TaskStatus.PENDING);

                        int insertIndex = -1;
                        int targetIndex = -1;
                        for (int i = 0; i < stages.size(); i++) {
                            if (stages.get(i).getId().equals(targetStageId)) {
                                insertIndex = i + 1;
                                targetIndex = i;
                                break;
                            }
                        }

                        if (targetIndex != -1 && targetIndex < currentStageIndex) {
                            return "错误：禁止在当前阶段之前插入新阶段。只能在当前阶段之后添加任务。";
                        }

                        if (insertIndex != -1) {
                            stages.add(insertIndex, newStage);
                        } else {
                            stages.add(newStage);
                        }
                        log.info("会话 [{}] 添加新阶段: {}", context.getSessionId(), newStage.getName());
                        resultMessage.append("- 成功添加新阶段：[").append(newStage.getName()).append("] (ID: ")
                                .append(newStage.getId()).append(")\n");

                        changeDetails.add(PlanChangeMessage.PlanChangeDetail.builder()
                                .changeType(ChangeType.PLAN_CHANGE.name())
                                .operation(PlanOperationType.ADD_STAGE.name())
                                .stageId(targetStageId)
                                .newStageId(newStage.getId())
                                .stageName(newStage.getName())
                                .description(newStage.getDescription())
                                .build());
                        break;

                    case DELETE_STAGE:
                        int targetDeleteIndex = -1;
                        for (int i = 0; i < stages.size(); i++) {
                            if (stages.get(i).getId().equals(targetStageId)) {
                                targetDeleteIndex = i;
                                break;
                            }
                        }
                        if (targetDeleteIndex == -1) {
                            return "错误：未找到ID为 " + targetStageId + " 的阶段。";
                        }
                        if (targetDeleteIndex <= currentStageIndex) {
                            return "错误：禁止删除已经完成或正在进行的阶段。";
                        }
                        stages.removeIf(s -> s.getId().equals(targetStageId));
                        log.info("会话 [{}] 删除了阶段: {}", context.getSessionId(), targetStageId);
                        resultMessage.append("- 成功删除阶段 (ID: ").append(targetStageId).append(")\n");

                        changeDetails.add(PlanChangeMessage.PlanChangeDetail.builder()
                                .changeType(ChangeType.PLAN_CHANGE.name())
                                .operation(PlanOperationType.DELETE_STAGE.name())
                                .stageId(targetStageId)
                                .build());
                        break;

                    case UPDATE_STAGE:
                        Stage targetUpdateStage = null;
                        int targetUpdateIndex = -1;
                        for (int i = 0; i < stages.size(); i++) {
                            Stage s = stages.get(i);
                            if (s.getId().equals(targetStageId)) {
                                targetUpdateStage = s;
                                targetUpdateIndex = i;
                                break;
                            }
                        }
                        if (targetUpdateStage == null) {
                            return "错误：未找到ID为 " + targetStageId + " 的阶段。";
                        }
                        if (targetUpdateIndex <= currentStageIndex) {
                            return "错误：禁止修改已经完成或正在进行的阶段。";
                        }
                        if (StringUtils.isNotEmpty(stageName))
                            targetUpdateStage.setName(stageName);
                        if (StringUtils.isNotEmpty(description))
                            targetUpdateStage.setDescription(description);
                        log.info("会话 [{}] 更新了阶段: {}", context.getSessionId(), targetStageId);
                        resultMessage.append("- 成功更新阶段：[").append(targetUpdateStage.getName()).append("] (ID: ")
                                .append(targetStageId).append(")\n");

                        changeDetails.add(PlanChangeMessage.PlanChangeDetail.builder()
                                .changeType(ChangeType.PLAN_CHANGE.name())
                                .operation(PlanOperationType.UPDATE_STAGE.name())
                                .stageId(targetStageId)
                                .stageName(targetUpdateStage.getName())
                                .description(targetUpdateStage.getDescription())
                                .build());
                        break;

                    case FINISH_CURRENT:
                        Stage finishStage = null;
                        int finishIndex = -1;
                        for (int i = 0; i < stages.size(); i++) {
                            if (stages.get(i).getId().equals(targetStageId)) {
                                finishStage = stages.get(i);
                                finishIndex = i;
                                break;
                            }
                        }
                        if (finishStage == null) {
                            return "错误：未找到ID为 " + targetStageId + " 的阶段。";
                        }
                        finishStage.setStatus(TaskStatus.COMPLETED);
                        log.info("会话 [{}] 完成了阶段: {}", context.getSessionId(), targetStageId);
                        resultMessage.append("- 成功完成阶段：[").append(finishStage.getName()).append("] (ID: ")
                                .append(targetStageId).append(")\n");

                        changeDetails.add(PlanChangeMessage.PlanChangeDetail.builder()
                                .changeType(ChangeType.STATUS_CHANGE.name())
                                .stageId(targetStageId)
                                .status(TaskStatus.COMPLETED.name())
                                .build());

                        // 流转到下一个阶段
                        if (finishIndex + 1 < stages.size()) {
                            Stage nextStage = stages.get(finishIndex + 1);
                            context.setCurrentStageId(nextStage.getId());
                            log.info("会话 [{}] 当前阶段流转为: {}", context.getSessionId(), context.getCurrentStageId());
                            resultMessage.append("  -> 提醒：下一步需要做的任务是 [").append(nextStage.getName()).append("] (ID: ")
                                    .append(nextStage.getId()).append(")\n");
                        } else {
                            log.info("会话 [{}] 的所有规划阶段均已完成!", context.getSessionId());
                            resultMessage.append("  -> 提醒：所有规划阶段均已完成！\n");
                        }
                        break;

                    default:
                        return "错误：不支持的操作类型 " + operation;
                }
            }

            if (!changeDetails.isEmpty()) {
                java.util.Map<String, Object> ctxMap = new java.util.HashMap<>();
                ctxMap.put(ContextKeyEnum.MODE.getKey(),
                        context.getExecutionMode() != null ? context.getExecutionMode().getMode() : "");
                if (context.getExecutionMode() == ModeEnum.PLAN_EXECUTOR
                        && context.getCurrentStageId() != null) {
                    ctxMap.put(ContextKeyEnum.STAGE_ID.getKey(), context.getCurrentStageId());
                }
                PlanChangeMessage changeMsg = PlanChangeMessage
                        .builder()
                        .context(Map.copyOf(ctxMap))
                        .messages(changeDetails)
                        .build();
                MessageUtils.sendMessage(context, changeMsg);
            }
        }

        return resultMessage.toString();
    }

    private SuperAgentContext getContext(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        Object obj = toolContext.getContext().get(ToolContextKeys.SESSION_CONTEXT);
        if (obj instanceof SuperAgentContext) {
            return (SuperAgentContext) obj;
        }
        return null;
    }

    @JsonClassDescription("更新计划的请求")
    public record Request(
            @JsonProperty(required = true) @JsonPropertyDescription("操作列表，按顺序执行") List<Operation> operations) {
    }

    @JsonClassDescription("单个计划更新操作")
    public record Operation(
            @JsonProperty(required = true) @JsonPropertyDescription("操作类型：START_CURRENT(开始当前阶段), FINISH_CURRENT(完成当前阶段), ADD_STAGE(添加阶段), DELETE_STAGE(删除阶段), UPDATE_STAGE(更新阶段)") String operation,
            @JsonProperty(required = true) @JsonPropertyDescription("目标阶段ID。ADD_STAGE时为上一个阶段ID(插在谁后面)；DELETE_STAGE/UPDATE_STAGE时为要操作的阶段ID；FINISH_CURRENT/START_CURRENT时为当前操作的阶段ID") String targetStageId,
            @JsonProperty(required = false) @JsonPropertyDescription("阶段名称，ADD_STAGE/UPDATE_STAGE时使用") String stageName,
            @JsonProperty(required = false) @JsonPropertyDescription("阶段描述，ADD_STAGE/UPDATE_STAGE时使用") String description) {
    }
}
