package org.gemo.apex.skills.learning;

import cn.hutool.core.util.IdUtil;
import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.constant.ToolNames;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.hook.tool.PostToolCallHook;
import org.gemo.apex.hook.tool.PostToolCallHookContext;
import org.gemo.apex.hook.tool.PostToolCallHookResult;
import org.gemo.apex.skills.learning.model.SkillUsageRecord;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component("skillUsageRecorderHook")
public class SkillUsageRecorderHook implements PostToolCallHook {

    private final SkillExperienceLearningProperties properties;
    private final SkillUsageRecordRepository usageRepository;

    public SkillUsageRecorderHook(SkillExperienceLearningProperties properties,
            SkillUsageRecordRepository usageRepository) {
        this.properties = properties;
        this.usageRepository = usageRepository;
    }

    @Override
    public PostToolCallHookResult apply(PostToolCallHookContext context) {
        try {
            if (!properties.isEnabled() || !ToolNames.ACTIVATE_SKILL.equals(context.getToolName())
                    || !context.isToolExecutionSucceeded()) {
                return PostToolCallHookResult.keep();
            }
            SuperAgentContext superAgentContext = context.getSuperAgentContext();
            if (superAgentContext == null || context.getArguments() == null || context.getArguments().get("command") == null) {
                return PostToolCallHookResult.keep();
            }
            String skillName = String.valueOf(context.getArguments().get("command"));
            usageRepository.insert(SkillUsageRecord.builder()
                    .id(IdUtil.simpleUUID())
                    .agentKey(context.getAgentKey())
                    .skillName(skillName)
                    .sessionId(context.getSessionId())
                    .turnNo(superAgentContext.getTurnNo())
                    .activationMessageSortNo(Math.max(1L, superAgentContext.getNextMessageSortNo() - 1L))
                    .createdTime(LocalDateTime.now())
                    .build());
        } catch (Exception ex) {
            log.warn("Failed to record skill usage, agentKey={}, sessionId={}, toolCallId={}, invocationId={}, skillName={}",
                    context.getAgentKey(), context.getSessionId(), context.getToolCallId(), context.getInvocationId(),
                    context.getArguments() != null ? context.getArguments().get("command") : null, ex);
        }
        return PostToolCallHookResult.keep();
    }
}
