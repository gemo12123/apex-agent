package org.gemo.apex.skills.learning;

import org.gemo.apex.memory.extract.PromptTemplateLoader;
import org.gemo.apex.skills.learning.model.SkillConversationSlice;
import org.gemo.apex.skills.learning.model.SkillSessionMessage;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SkillExperiencePromptService {

    private final SkillExperienceLearningProperties properties;
    private final PromptTemplateLoader promptTemplateLoader;

    public SkillExperiencePromptService(SkillExperienceLearningProperties properties,
            PromptTemplateLoader promptTemplateLoader) {
        this.properties = properties;
        this.promptTemplateLoader = promptTemplateLoader;
    }

    public String buildPrompt(String agentKey, String skillName, String existingExperience,
            List<SkillConversationSlice> slices) {
        String template = promptTemplateLoader.load(properties.getExperiencePrompt());
        return template
                .replace("{agentKey}", safe(agentKey))
                .replace("{skillName}", safe(skillName))
                .replace("{existingExperience}", safe(existingExperience))
                .replace("{conversationSlices}", renderSlices(slices));
    }

    private String renderSlices(List<SkillConversationSlice> slices) {
        if (slices == null || slices.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (SkillConversationSlice slice : slices) {
            builder.append("session=").append(slice.sessionId())
                    .append(", activation=").append(slice.activationMessageSortNo())
                    .append("\n");
            for (SkillSessionMessage message : slice.messages()) {
                builder.append("[").append(message.role()).append("]");
                if (message.toolName() != null) {
                    builder.append(" tool=").append(message.toolName());
                }
                builder.append(" ").append(safe(message.content())).append("\n");
            }
        }
        return builder.toString().trim();
    }

    private String safe(String value) {
        return value != null ? value : "";
    }
}
