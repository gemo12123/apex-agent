package org.gemo.apex.skills.learning;

import org.gemo.apex.memory.session.SessionContextStore;
import org.gemo.apex.skills.learning.model.SkillConversationSlice;
import org.gemo.apex.skills.learning.model.SkillSessionMessage;
import org.gemo.apex.skills.learning.model.SkillUsageRecord;
import org.gemo.apex.skills.learning.model.SkillUsageValidationResult;
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
        if (payload == null || !payload.contains("\"activate_skill\"")) {
            return SkillUsageValidationResult.invalid(record, "activate_skill payload missing");
        }
        if (!payload.contains(record.getSkillName())) {
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
}
