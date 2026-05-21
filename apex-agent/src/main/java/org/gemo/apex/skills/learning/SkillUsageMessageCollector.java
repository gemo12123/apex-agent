package org.gemo.apex.skills.learning;

import org.gemo.apex.memory.session.SessionContextStore;
import org.gemo.apex.skills.learning.model.SkillConversationSlice;
import org.gemo.apex.skills.learning.model.SkillSessionMessage;
import org.gemo.apex.skills.learning.model.SkillUsageRecord;
import org.gemo.apex.skills.learning.model.SkillUsageValidationResult;
import org.gemo.apex.util.JacksonUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class SkillUsageMessageCollector {

    private final SkillExperienceLearningProperties properties;
    private final SessionContextStore sessionContextStore;

    public SkillUsageMessageCollector(SkillExperienceLearningProperties properties,
            SessionContextStore sessionContextStore) {
        this.properties = properties;
        this.sessionContextStore = sessionContextStore;
    }

    public SkillUsageValidationResult validate(SkillUsageRecord record) {
        List<SkillSessionMessage> messages = sessionContextStore.loadSkillSessionMessages(record.getSessionId());
        SkillSessionMessage activationMessage = messages.stream()
                .filter(message -> Objects.equals(message.sortNo(), record.getActivationMessageSortNo()))
                .findFirst()
                .orElse(null);
        if (activationMessage == null) {
            return SkillUsageValidationResult.invalid(record, "activation message not found");
        }
        if (!"activate_skill".equals(activationMessage.toolName())) {
            return SkillUsageValidationResult.invalid(record, "tool_name mismatch");
        }
        String payload = activationMessage.messagePayload();
        if (payload == null || payload.isBlank()) {
            return SkillUsageValidationResult.invalid(record, "activate_skill payload missing");
        }
        if (!matchesActivatedSkill(payload, record.getSkillName())) {
            return SkillUsageValidationResult.invalid(record, "skill_name mismatch");
        }
        return SkillUsageValidationResult.valid(record);
    }

    public List<SkillConversationSlice> collectValidSlices(List<SkillUsageRecord> records) {
        List<SkillConversationSlice> slices = new ArrayList<>();
        for (SkillUsageRecord record : records) {
            SkillUsageValidationResult validation = validate(record);
            if (!validation.isValid()) {
                continue;
            }
            List<SkillSessionMessage> allMessages = sessionContextStore.loadSkillSessionMessages(record.getSessionId());
            List<SkillSessionMessage> sliceMessages = allMessages.size() <= properties.getLongSessionMessageThreshold()
                    ? allMessages
                    : windowAround(allMessages, record.getActivationMessageSortNo());
            slices.add(new SkillConversationSlice(record.getSessionId(), record.getActivationMessageSortNo(),
                    sliceMessages));
        }
        return slices;
    }

    private List<SkillSessionMessage> windowAround(List<SkillSessionMessage> messages, Long activationSortNo) {
        int activationIndex = -1;
        for (int index = 0; index < messages.size(); index++) {
            if (Objects.equals(messages.get(index).sortNo(), activationSortNo)) {
                activationIndex = index;
                break;
            }
        }
        if (activationIndex < 0) {
            return List.of();
        }
        int fromIndex = Math.max(0, activationIndex - properties.getActivationWindowBefore());
        int toIndex = Math.min(messages.size(), activationIndex + properties.getActivationWindowAfter() + 1);
        return messages.subList(fromIndex, toIndex);
    }

    private boolean matchesActivatedSkill(String payload, String expectedSkillName) {
        try {
            var root = JacksonUtils.toTree(payload);
            var toolCalls = root.path("toolCalls");
            if (!toolCalls.isArray()) {
                return false;
            }
            for (var toolCallNode : toolCalls) {
                if (!"activate_skill".equals(toolCallNode.path("name").asText(null))) {
                    continue;
                }
                String arguments = toolCallNode.path("arguments").asText(null);
                if (arguments == null || arguments.isBlank()) {
                    continue;
                }
                String command = JacksonUtils.toTree(arguments).path("command").asText(null);
                if (expectedSkillName.equals(command)) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
