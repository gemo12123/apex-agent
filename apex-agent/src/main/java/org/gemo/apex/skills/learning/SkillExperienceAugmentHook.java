package org.gemo.apex.skills.learning;

import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.constant.ToolNames;
import org.gemo.apex.hook.tool.PostToolCallHook;
import org.gemo.apex.hook.tool.PostToolCallHookContext;
import org.gemo.apex.hook.tool.PostToolCallHookResult;
import org.gemo.apex.skills.learning.model.SkillExperienceMemory;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component("skillExperienceAugmentHook")
public class SkillExperienceAugmentHook implements PostToolCallHook {

    private final SkillExperienceLearningProperties properties;
    private final SkillExperienceMemoryRepository experienceRepository;

    public SkillExperienceAugmentHook(SkillExperienceLearningProperties properties,
            SkillExperienceMemoryRepository experienceRepository) {
        this.properties = properties;
        this.experienceRepository = experienceRepository;
    }

    @Override
    public PostToolCallHookResult apply(PostToolCallHookContext context) {
        try {
            if (!ToolNames.ACTIVATE_SKILL.equals(context.getToolName())) {
                return PostToolCallHookResult.keep();
            }
            if (context.getArguments() == null || context.getArguments().get("command") == null
                    || context.getCurrentResult() == null) {
                return PostToolCallHookResult.keep();
            }
            String skillName = String.valueOf(context.getArguments().get("command"));
            Optional<SkillExperienceMemory> experience = experienceRepository.find(context.getAgentKey(), skillName);
            if (experience.isEmpty()) {
                return PostToolCallHookResult.keep();
            }
            String section = """
                    # %s

                    以下经验来自该 Skill 在当前 Agent 下的历史使用总结，仅供参考。
                    请结合当前任务判断，不要完全依赖这些经验；当经验与当前上下文冲突时，以当前任务事实为准。

                    %s
                    """.formatted(properties.getExperienceSectionTitle(), experience.get().getContent());
            String replaced = context.getCurrentResult().replace("</instructions>", "\n\n" + section + "\n</instructions>");
            return PostToolCallHookResult.replaceResult(replaced);
        } catch (Exception ex) {
            log.warn("Failed to augment skill result, agentKey={}, sessionId={}, toolCallId={}, invocationId={}",
                    context.getAgentKey(), context.getSessionId(), context.getToolCallId(), context.getInvocationId(), ex);
            return PostToolCallHookResult.keep();
        }
    }
}
