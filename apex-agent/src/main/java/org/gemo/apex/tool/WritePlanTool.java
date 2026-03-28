package org.gemo.apex.tool;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.constant.ContextKeyEnum;
import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.constant.ToolContextKeys;
import org.gemo.apex.constant.ToolNames;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.domain.Plan;
import org.gemo.apex.message.PlanDeclaredMessage;
import org.gemo.apex.util.MessageUtils;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PlanExecutor 模式初始：强制要求写入详细规划步骤
 */
@Slf4j
public class WritePlanTool {

    @Tool(name = ToolNames.WRITE_PLAN, description = "在 PlanExecutor 模式初期强制调用的工具。你需要向系统提交你针对任务拆解制定的分布执行计划。")
    public String write_plan(Request request, ToolContext toolContext) {

        SuperAgentContext context = getContext(toolContext);
        if (context == null) {
            return "写入规划失败：无法获取会话上下文。";
        }

        Plan plan = new Plan();
        List<org.gemo.apex.domain.Stage> domainStages = new ArrayList<>();

        if (request != null && request.stages() != null) {
            for (Stage dtoStage : request.stages()) {
                org.gemo.apex.domain.Stage stage = new org.gemo.apex.domain.Stage();
                stage.setId(dtoStage.stageId());
                stage.setName(dtoStage.name());
                stage.setDescription(dtoStage.description());
                stage.setExpectedOutput(dtoStage.expectedOutput());
                if (dtoStage.skills() != null) {
                    stage.setSkills(new ArrayList<>(dtoStage.skills()));
                }
                domainStages.add(stage);
            }
        }

        plan.setStages(domainStages);
        context.setPlan(plan);
        if (!domainStages.isEmpty()) {
            context.setCurrentStageId(domainStages.getFirst().getId());
        }

        // 构造前台消息
        java.util.List<PlanDeclaredMessage.StageMessage> declaredStages = new ArrayList<>();
        for (org.gemo.apex.domain.Stage s : domainStages) {
            declaredStages.add(PlanDeclaredMessage.StageMessage.builder()
                    .stageId(s.getId())
                    .stageName(s.getName())
                    .description(s.getDescription())
                    .status(s.getStatus().name())
                    .build());
        }
        java.util.Map<String, Object> ctxMap = new java.util.HashMap<>();
        ctxMap.put(ContextKeyEnum.MODE.getKey(),
                context.getExecutionMode() != null ? context.getExecutionMode().getMode() : "");
        if (context.getExecutionMode() == ModeEnum.PLAN_EXECUTOR
                && context.getCurrentStageId() != null) {
            ctxMap.put(ContextKeyEnum.STAGE_ID.getKey(), context.getCurrentStageId());
        }
        PlanDeclaredMessage planMsg = PlanDeclaredMessage.builder()
                .context(Map.copyOf(ctxMap))
                .messages(declaredStages)
                .build();
        MessageUtils.sendMessage(context, planMsg);

        log.info("Agent 会话 [{}] 已成功保存执行计划，共 {} 个阶段。", context.getSessionId(), domainStages.size());
        return "PLAN_INITIALIZED_AND_SAVED";
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

    @JsonClassDescription("写入规划的请求")
    public record Request(
            @JsonProperty(required = true) @JsonPropertyDescription("规划阶段列表") List<Stage> stages) {
    }

    @JsonClassDescription("单个可执行规划阶段，每个阶段需要包含阶段名称、阶段描述")
    public record Stage(
            @JsonProperty(required = true) @JsonPropertyDescription("阶段ID，要求非空且唯一，避免包含特殊字符") String stageId,
            @JsonProperty(required = true) @JsonPropertyDescription("阶段名称，明确该阶段要达成的目标，10个字以内，不需要带序号") String name,
            @JsonProperty(required = true) @JsonPropertyDescription("阶段描述，阐述该阶段需要执行的具体操作，50字以内") String description,
            @JsonProperty(required = false) @JsonPropertyDescription("该阶段需要使用的技能列表") List<String> skills,
            @JsonProperty(required = false) @JsonPropertyDescription("该阶段的预期输出，50字以内") String expectedOutput) {
    }
}
