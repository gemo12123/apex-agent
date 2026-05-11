package org.gemo.apex.skills.learning;

import org.gemo.apex.skills.learning.model.SkillConversationSlice;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class SkillExperienceExtractor {

    private final ChatClient chatClient;
    private final SkillExperiencePromptService promptService;

    public SkillExperienceExtractor(ChatClient chatClient, SkillExperiencePromptService promptService) {
        this.chatClient = chatClient;
        this.promptService = promptService;
    }

    public String regenerate(String agentKey, String skillName, String existingExperience,
            List<SkillConversationSlice> slices) {
        if (slices == null || slices.isEmpty()) {
            return existingExperience;
        }
        String prompt = promptService.buildPrompt(agentKey, skillName, existingExperience, slices);
        return Optional.ofNullable(chatClient.prompt(prompt).call().content())
                .map(String::trim)
                .filter(content -> !content.isBlank())
                .orElse(existingExperience);
    }
}
